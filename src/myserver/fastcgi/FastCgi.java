package myserver.fastcgi;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;

import myserver.util.Util;


/**
 * FastCgi process class
 * @author Fan Yang
 *
 */
public class FastCgi {

	private final String FASTCGI_HOST;
	private final int FASTCGI_PORT;
	private final String FASTCGI_ROOT;
	
	/**
	 * setup fastcgi
	 */
	public FastCgi(Properties properties) {
		
		FASTCGI_HOST = properties.getProperty("FASTCGI_HOST");
		FASTCGI_PORT = Integer.valueOf(properties.getProperty("FASTCGI_PORT"));
		FASTCGI_ROOT = properties.getProperty("FASTCGI_ROOT");
	}

	
	/**
	 * send request to fastcgi client
	 * then receive from fastcgi client
	 * then response to http client
	 */
	public void requestFastCgi(AsynchronousSocketChannel asynSocket, 
				String path, String query){
		
		byte[] responseByte = "HTTP/1.1 200 OK\n".getBytes();
		byte[] fastCgiRequest = contructFastCgiHeader(path, query);
		
		try (Socket socket = new Socket(FASTCGI_HOST, FASTCGI_PORT);) {
			
			InputStream socketInputStream = socket.getInputStream();
			OutputStream socketOutputStream = socket.getOutputStream();
			socketOutputStream.write(fastCgiRequest);
			
			//Parse fastcgi response
			byte[] buffer = new byte[65536];

			while (true) {
				socketInputStream.read(buffer, 0, 8);
				if (buffer[1] != 6) { //end
					break;
				}
				
				int high = buffer[4];
				if (high < 0) high += 256;
				int low = buffer[5];
				if (low < 0) low += 256;
				int contentLength = (high<<8) + low;
				int padding = buffer[6];
				
				socketInputStream.read(buffer, 0, contentLength + padding);
				
				responseByte = Util.concateByteArray(responseByte, buffer, contentLength);
			}
			
			buffer = null;
			
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		asynSocket.write(ByteBuffer.wrap(responseByte));
	}
	

	/**
	 * contruct fastcgi header for fastcgi client
	 */
	private byte[] contructFastCgiHeader(String path, String query) {
		
		Map<String, String> headerParams = new HashMap<String, String>();
		headerParams.put("SCRIPT_FILENAME", FASTCGI_ROOT + path);
		headerParams.put("QUERY_STRING", query);
		headerParams.put("REQUEST_METHOD", "GET");
		headerParams.put("GATEWAY_INTERFACE", "CGI/1.1");
		headerParams.put("SERVER_SOFTWARE", "MyServer/0.0.1");
		
//		headerParams.put("CONTENT_TYPE", "");
//		headerParams.put("CONTENT_LENGTH", "");
//		headerParams.put("SCRIPT_NAME", path);
//		headerParams.put("REQUEST_URI", path + query);
//		headerParams.put("DOCUMENT_URI", path);
//		headerParams.put("DOCUMENT_ROOT", FASTCGI_ROOT);
//		headerParams.put("SERVER_PROTOCOL", "HTTP1.1");
//		headerParams.put("REMOTE_ADDR", "127.0.0.1");
//		headerParams.put("SERVER_PORT", "80");
//		headerParams.put("SERVER_NAME", "localhost");
//		headerParams.put("REDIRECT_STATUS", "200");
//		headerParams.put("HTTP_ACCEPT", "text/html, application/xhtml+xml, */*");
//		headerParams.put("HTTP_ACCEPT_LANGUAGE", "en-Us");
//		headerParams.put("HTTP_USER_AGENT", "");
//		headerParams.put("HTTP_ACCEPT_ENCODING", "gzip, deflate");
//		headerParams.put("HTTP_HOST", "127.0.0.1");
//		headerParams.put("HTTP_DNT", "1");
//		headerParams.put("HTTP_CONNECTION", "Keep-Alive");
		
		LinkedList<Byte> requestByteList = new LinkedList<Byte>();
		Set<String> keySet = headerParams.keySet();
		for (String key : keySet) {
			String value = headerParams.get(key);
			int keyLength = key.length();
			int valueLength = value.length();
			
			requestByteList.add((byte)keyLength);
			requestByteList.add((byte)valueLength);
			for (int i = 0; i < keyLength; i++) {
				requestByteList.add((byte)(key.charAt(i)));
			}
			for (int i = 0; i < valueLength; i++) {
				requestByteList.add((byte)(value.charAt(i)));
			}
		}
		int fill = 0;
		while (requestByteList.size()%8 != 0) {
			requestByteList.add((byte)0);
			fill++;			
		}
		
		byte[] requestBegin = {1, 1, 0, 1, 0, 8, 0, 0, 
				0, 1, 0, 0, 0, 0, 0, 0, 
				1, 4, 0, 1, 0, 0, 0, 0};
		byte[] requestEnd = {1, 4, 0, 1, 0, 0, 0, 0, 
				1, 5, 0, 1, 0, 0, 0, 0};
		requestBegin[20] = (byte)((requestByteList.size())/256);
		requestBegin[21] = (byte)((requestByteList.size() - fill)%256);
		requestBegin[22] = (byte)fill;
		
		byte[] requestByte = new byte[requestBegin.length 
		                              + requestByteList.size()
		                              + requestEnd.length];
		System.arraycopy(requestBegin, 0, requestByte, 0, requestBegin.length);
		int contentLength = requestByteList.size();
		for (int i = 0; i < contentLength; i++) {
			requestByte[i + requestBegin.length] = requestByteList.pop();
		}
		System.arraycopy(requestEnd, 0, 
				requestByte, requestBegin.length + contentLength, requestEnd.length);
		
		return requestByte;
	}
	
	
	
	
	
}



