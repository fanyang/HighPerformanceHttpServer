package myserver.server;

import java.io.FileReader;
import java.util.Properties;


/**
 * Main class to start the program
 * @author Fan Yang
 *
 */
public class MyServerMain {

	public static void main(String[] args) throws Exception {
		
		Properties properties = new Properties();
		FileReader fr = new FileReader("conf/config.properties");
		properties.load(fr);
		
		fr.close();
		
		new MyServer(properties).start();


	}

}
