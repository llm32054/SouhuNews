package main.java.SohuSpider.util;

import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {

	/**
	 * 写txt文本文件
	 * @param path
	 * @param content
	 */
	public static void Write(String path, String content) {
		try {
			FileWriter fw = new FileWriter(path, true);
			fw.write(content);
			// 1.3刷新输出流
			fw.flush();
			// 1.4关闭输出流
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
