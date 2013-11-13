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
		FileReader propertiesFileReader = new FileReader("conf/config.properties");
		properties.load(propertiesFileReader);
		propertiesFileReader.close();
		
		MyServer.start(properties);


	}

}
