package myserver.server;

import java.util.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import myserver.fastcgi.FastCgi;
import myserver.util.ArrayUtil;


/**
 * MyServer class
 * @author Fan Yang
 *
 */
public class MyServer {
	
	private final String HOST;
	private final int PORT;
	private final String DOC_ROOT;
	private final String INDEX_PAGE;
	private final String ERROR_PAGE;
	private final int REQUEST_LENGTH;
	private final long FILE_CACHE;
	private final int THREAD_POOL;
	private final int BACKLOG;
	private final String LOG_XML;
	

	private AsynchronousServerSocketChannel serverSocketChannel;
	
	private Map<String, byte[]> files = new ConcurrentSkipListMap<>();
	private final static Map<String, byte[]> headers = new HashMap<>();
	private List<String> filePathesList = new LinkedList<>();
	private long currentCacheSize = 0;
	
	private FastCgi fastCgi;
	private Logger logger;

	/**
	 * Init MyServer
	 * @param properties
	 * @throws Exception
	 */
	private MyServer(Properties properties) throws Exception {
		
		HOST = properties.getProperty("HOST");
		PORT = Integer.valueOf(properties.getProperty("PORT"));
		DOC_ROOT = properties.getProperty("DOC_ROOT");
		INDEX_PAGE = properties.getProperty("INDEX_PAGE");
		ERROR_PAGE = properties.getProperty("ERROR_PAGE");
		REQUEST_LENGTH = Integer.valueOf(properties.getProperty("REQUEST_LENGTH"));
		FILE_CACHE = Long.valueOf(properties.getProperty("FILE_CACHE"));
		THREAD_POOL = Integer.valueOf(properties.getProperty("THREAD_POOL"));
		BACKLOG = Integer.valueOf(properties.getProperty("BACKLOG"));
		LOG_XML = properties.getProperty("LOG_XML");
		
		
		generateHttpHeaders();
		files.put(INDEX_PAGE, ArrayUtil.concateByteArray(headers.get("html"), loadFile(INDEX_PAGE)));
		String errorHeader = "HTTP/1.1 404 Not Found\nContent-Encoding: gzip\nContent-Type: text/html\n\n";
		files.put(ERROR_PAGE, ArrayUtil.concateByteArray(errorHeader.getBytes(), loadFile(ERROR_PAGE)));
		
		serverSocketChannel = AsynchronousServerSocketChannel
				.open(AsynchronousChannelGroup.withCachedThreadPool(
						Executors.newCachedThreadPool(), THREAD_POOL))
				.bind(new InetSocketAddress(HOST, PORT), BACKLOG);
		
		fastCgi = new FastCgi(properties);
		System.setProperty("log4j.configurationFile", "conf/" + LOG_XML);
		logger = LogManager.getLogger();
		
		System.out.println("Server start on " + HOST + ":" + PORT);
	}


	public static void start(Properties properties) throws Exception {
		
		MyServer myServer = new MyServer(properties);
		myServer.serverSocketChannel.accept(null, myServer.new ServerSocketCompletionHandler());
		
		while (true) {
			Thread.sleep(Long.MAX_VALUE);
		}

	}
	
	/**
	 * Process request when server socket accept
	 * @author Fan Yang
	 *
	 */
	private class ServerSocketCompletionHandler 
		implements CompletionHandler<AsynchronousSocketChannel, Object> {


		@Override
		public void completed(AsynchronousSocketChannel socket, Object attachment) {
			
			serverSocketChannel.accept(null, this);
			
			ByteBuffer requestBuffer = ByteBuffer.allocate(REQUEST_LENGTH);
			
			socket.read(requestBuffer, null
					, new SocketReadCompletionHandler(socket, requestBuffer));
		}

		
		@Override
		public void failed(Throwable exc, Object attachment) {
			exc.printStackTrace();
		}
		
	}
	
	

	private class SocketReadCompletionHandler implements CompletionHandler<Integer, Object> {
		
		private AsynchronousSocketChannel socket;
		private ByteBuffer requestBuffer;
		
		public SocketReadCompletionHandler(AsynchronousSocketChannel socket, ByteBuffer requestBuffer) {
			this.socket = socket;
			this.requestBuffer = requestBuffer;
		}

		@Override
		public void completed(Integer requestLength, Object attachment) {
			//Parse HTTP request
			if (requestLength > 0) {
				String requestString = new String(requestBuffer.array(), 0, requestLength);
				
				int firstIndex, lastIndex;
				firstIndex = requestString.indexOf('\n');
				if (firstIndex == -1) return;
				String firstLine = requestString.substring(0, firstIndex);
				
				firstIndex = firstLine.indexOf(' ');
				lastIndex = firstLine.lastIndexOf(' ');
				if (firstIndex < 0 || lastIndex < 0 || firstIndex >= lastIndex) return;
				String queryUri = firstLine.substring(firstIndex+1, lastIndex);

				firstIndex = queryUri.indexOf('?');
				String filePath = "";
				String query = "";
				if (firstIndex < 0) {
					filePath = queryUri;
				} else {
					filePath = queryUri.substring(0, firstIndex);
					query = queryUri.substring(firstIndex + 1);
				}
				if (filePath.equals("/")) filePath = INDEX_PAGE;
				
				//read request file
				byte[] fileBytes = files.get(filePath);
				
				//file not in cache
				if (fileBytes == null) {

					String fileExt = "";
					lastIndex = filePath.lastIndexOf('.');
					fileExt = filePath.substring(lastIndex+1);
					
					
					if (fileExt.equals("php")) {
						//support FastCgi
						fileBytes = fastCgi.requestFastCgi(filePath, query);
					} else {
						if (!headers.containsKey(fileExt)) fileExt = "txt";
						fileBytes = loadFile(filePath);
						if (fileBytes == null) {
							fileBytes = files.get(ERROR_PAGE);
							//Cache the error path
							files.put(filePath, fileBytes);
						} else { //load file from disk to cache
							byte[] retByteArray = ArrayUtil.concateByteArray(headers.get(fileExt), fileBytes);
							if (files.put(filePath, retByteArray) == null) {
								currentCacheSize += fileBytes.length;
								filePathesList.add(filePath);
							}
							fileBytes = retByteArray;
							
							
							//Cache algorithm: FIFO
							while(currentCacheSize > FILE_CACHE) 
								currentCacheSize -= files.remove(filePathesList.remove(0)).length;
							
						}
						
					}
					
				}
				
				//error log
				if (fileBytes == files.get(ERROR_PAGE)) {
					try {
						logger.error("{} - {}"
								, ((InetSocketAddress) socket.getRemoteAddress()).getHostString()
								, filePath);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
				
				//write file to client
				socket.write(ByteBuffer.wrap(fileBytes), null, new CompletionHandler<Integer, Object>() {

					@Override
					public void completed(Integer result,
							Object attachment) {
						try {
							socket.close();
						} catch (IOException ex) {
							ex.printStackTrace();
						}
						
					}

					@Override
					public void failed(Throwable exc, Object attachment) {
						exc.printStackTrace();
					}
				});
			}
		}

		
		@Override
		public void failed(Throwable exc, Object attachment) {
			exc.printStackTrace();
		}
		
	}
	
	
	/**
	 * load file from disk into byte array
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
	private byte[] loadFile(String fileName) {
		
		byte[] fileBuffer = null;
		try {
			fileBuffer = Files.readAllBytes(Paths.get(DOC_ROOT, fileName));
		} catch (IOException e1) {
			return null;
		}

		//Save Gzip compressed file
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] fileBytes = null;
		try(GZIPOutputStream gos = new GZIPOutputStream(baos);) {
			gos.write(fileBuffer);
			gos.finish();
			fileBytes = baos.toByteArray();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		 
		return fileBytes;
	}
	
	
	/**
	 * generate HTTP headers for different types
	 */
	private void generateHttpHeaders() {
		
		String[][] fileTypes = {
					{"html", "text/html"},
					{"css", "text/css"}, 
					{"js", "application/javascript"}, 
					{"png", "image/png"}, 
					{"jpg", "image/jpeg"}, 
					{"gif", "image/gif"}, 
					{"ico", "image/x-icon"}, 
					{"txt", "text/plain"},
				};
		
		for (String fileType[] : fileTypes) {
			String typeString = fileType[1];
			String httpHeader = 
					"HTTP/1.1 200 OK\nContent-Encoding: gzip\nContent-Type: " 
							+ typeString + "\n\n";

			headers.put(fileType[0], httpHeader.getBytes());
		}
		
	}
	
	

}




