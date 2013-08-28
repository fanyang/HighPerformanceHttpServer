package myserver.util;

/**
 * Utilities of My Server
 * @author Fan Yang
 *
 */
public class Util {

	/**
	 * concatenate two array
	 * @param byteArray1
	 * @param byteArray2
	 * @return
	 */
	public static byte[] concateByteArray(byte[] byteArray1, byte[] byteArray2) {
		
		byte[] result = new byte[byteArray1.length + byteArray2.length];
		System.arraycopy(byteArray1, 0, result, 0, byteArray1.length);
		System.arraycopy(byteArray2, 0, result, byteArray1.length, byteArray2.length);
		return result;
		
	}
	
	/**
	 * concatenate two array
	 * @param byteArray1
	 * @param byteArray2
	 * @param length length want to concatenate
	 * @return
	 */
	public static byte[] concateByteArray(byte[] byteArray1
			, byte[] byteArray2, int length) {
		
		byte[] result = new byte[byteArray1.length + length];
		System.arraycopy(byteArray1, 0, result, 0, byteArray1.length);
		System.arraycopy(byteArray2, 0, result, byteArray1.length, length);
		return result;
		
	}
}

