package main.java.SohuSpider;

public class Global {

	/**
	 * 新闻文本输出目录
	 */
	public static String newsOutDir = "E:\\shnews\\";
	/**
	 * 关键词输出文件
	 */
	public static String keyWordsFile = "E:\\shnews\\123456789.txt";
	/**
	 * 文件命名（以数字开始）
	 */
	public static int num = 1;
	/**
	 * 爬虫的新闻列表页面
	 */
	public static String urlNavigation =
			"http://127.0.0.1:8080/index.html";
	/**
	 * 提取URL的正则表达式
	 */
	public static String pattern = "^http://www.sohu.com/a/36.*";

}
