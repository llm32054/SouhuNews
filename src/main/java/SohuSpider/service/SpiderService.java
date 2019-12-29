package main.java.SohuSpider.service;

import static main.java.SohuSpider.util.JSoupUtils.getDocument;
import static main.java.SohuSpider.util.XmlUtils.writeEntryUrls;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import main.java.SohuSpider.Global;
import main.java.SohuSpider.bean.NewsBean;
import main.java.SohuSpider.filter.BloomFilter;
import main.java.SohuSpider.util.FileUtils;

public class SpiderService implements Serializable {

	/**
	 * 新闻输出目录
	 */
	public static String newsOutDir = Global.newsOutDir;
	/**
	 * 关键词输出文件
	 */
	public static String keyWordsFile = Global.keyWordsFile;

	// 文件命名
	public static volatile int num = Global.num;

	// 爬取的页面
	static String urlNavigation = Global.urlNavigation;

	// 使用BloomFilter算法去重
	static BloomFilter filter = new BloomFilter();

	// url阻塞队列
	BlockingQueue<String> urlQueue = null;

	// 线程池
	static Executor executor = Executors.newFixedThreadPool(20);

	// 爬取深度
	static int DEFAULT_DEPTH = 10;

	static int DEFAULT_THREAD_NUM = 10;

	public void start() throws InterruptedException {

		File urlsSer = new File("urlQueue.ser");
		if (urlsSer.exists()) {

			try {
				// 对象反序列化
				ObjectInputStream ois = new ObjectInputStream(
						new FileInputStream(urlsSer));
				urlQueue = (BlockingQueue<String>) ois.readObject();

				ois.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			// 创建阻塞队列
			urlQueue = new LinkedBlockingQueue<String>();

			// 获取新闻列表页面的新闻URL
			List<String> urlChannels = genEntryChannel(urlNavigation);

			for (String url : urlChannels) {
				urlQueue.add(url);
				System.out.println(url);
			}
		}

		// 添加程序监听结束,程序结束时候应序列化两个重要对象--urlQueue和filter
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			public void run() {
				System.out.println(urlQueue.isEmpty());
				try {
					if (urlQueue.isEmpty() == false) {
						// 序列化urlQueue
						ObjectOutputStream os = new ObjectOutputStream(
								new FileOutputStream("urlQueue.ser"));
						os.writeObject(urlQueue);
						os.close();

					}

					// 序列化bits
					ObjectOutputStream os = new ObjectOutputStream(
							new FileOutputStream("bits.ser"));
					os.writeObject(filter.getBitset());
					os.close();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}));

		for (int i = 0; i < DEFAULT_THREAD_NUM; i++) {
			Thread a = new Thread(new Runnable() {

				public void run() {
					while (true) {
						String url = getAUrl();
						if (!filter.contains(url)) {
							filter.add(url);
							System.out.println(
									Thread.currentThread().getName()
											+ "正在爬取url:" + url);
							if (url != null) {
								crawler(url);
							}
						} else {
							System.out.println("此url存在，不爬了." + url);
						}
					}

				}

			});
			executor.execute(a);
		}

		// 线程池监视线程
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try {
						if (((ThreadPoolExecutor) executor)
								.getActiveCount() < 10) {
							Thread a = new Thread(new Runnable() {
								public void run() {
									while (true) {
										String url = getAUrl();
										if (!filter.contains(url)) {
											filter.add(url);
											System.out.println(Thread
													.currentThread()
													.getName()
													+ "正在爬取url:" + url);
											if (url != null) {
												crawler(url);
											}
										} else {
											System.out.println(
													"此url存在， 不爬了."
															+ url);
										}
									}
								}
							});
							executor.execute(a);
							if (urlQueue.size() == 0) {
								System.out.println("队列为0了！！！！！！！");
							}
						}
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

			}

		}).start();

	}

	/**
	 * 解析新闻列表页的新闻详情URL
	 * @param startUrl 新闻列表页URL
	 * @return
	 */
	public static List<String> genEntryChannel(String startUrl) {
		// 存储新闻详情URL
		List<String> urlArray = new ArrayList<String>();
		// 匹配新闻详情URL的正则表达式
		String pattern = Global.pattern;
		// 获取新闻列表页的HTML对象
		Document doc = getDocument(startUrl);
		// 从HTML对象中解析新闻详情页的URL
		Elements Urls = doc.select("div h4 a");
		// 解析所有的新闻详情页URL，添加至urlArray对象中
		for (Element url : Urls) {
			String link = url.attr("href");
			if (Pattern.matches(pattern, link) == true) {
				urlArray.add(link);
			}
		}
		writeEntryUrls(urlArray);
		return urlArray;
	}

	/**
	 * 提取新闻内容（URL,新闻标题和新闻正文）
	 * @param url 新闻详情页面URL
	 */
	public synchronized void crawler(String url) {
		// 获取新闻页的HTML对象
		Document doc = getDocument(url);
		// 定义正则表达式（再次验证）
		String pattern = Global.pattern;
		// 正则匹配成功
		if (Pattern.matches(pattern, url)) {
			// 新闻标题
			String title = "";
			// 新闻关键词
			String keywords = "";
			String category = null;
			// 定义新闻实体对象
			NewsBean news = new NewsBean();
			news.setUrl(url);

			// 解析新闻标题
			// title = doc.title();
			title = doc.body().select(".text-title h1").text();

			// 解析新闻正文
			Elements Contents = null;
			Contents = doc.body().select(".article p");
			String cont = "";
			if (Contents.isEmpty() == false) {
				int i = 0;
				for (Element con : Contents) {
					// 正文中也包含有标题，导致标题重复
					if (i != 0) {
						cont = cont + con.text() + "\r\n";
					}
					++i;
				}
			}

			// 解析新闻关键词
			Elements keyw = null;
			keyw = doc.body().select(".article-bottom-banner a");
			if (keyw.isEmpty() == false) {
				int i = 0;
				for (Element key : keyw) {
					if (i++ != (keyw.size() - 1)) {
						keywords = keywords + key.text() + "，";
					} else {// 最后一个新闻关键词不加逗号
						keywords = keywords + key.text();
					}
				}

			}
			news.setContent(cont);
			news.setCategory(category);
			news.setTitle(title);
			news.setKeyword(keywords);

			// 打印新闻信息
			System.out.println("爬取成功：" + news);
			// 关键词不为空的新闻输出为文本文件
			if (!("").equals(keywords.trim())) {
				FileUtils.Write(newsOutDir + (num) + ".txt",
						news.getUrl() + "\r\n");
				FileUtils.Write(newsOutDir + (num) + ".txt",
						news.getTitle() + "\r\n");
				FileUtils.Write(newsOutDir + (num) + ".txt",
						news.getContent() + "\r\n");
				FileUtils.Write(keyWordsFile,
						(num++) + "：" + news.getKeyword() + "\r\n");
			}
		}
	}

	public String getAUrl() {
		String tmpAUrl;
		try {
			tmpAUrl = urlQueue.take();
			return tmpAUrl;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

}
