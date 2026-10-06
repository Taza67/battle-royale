package outside.communication;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ByteUtilities {
	// Retourne un tableau d'octets représentant un objet
	public static byte[] objectToByteArray(Object obj) {
		byte[] ba = null;

		try {
	        ByteArrayOutputStream baOutStream = new ByteArrayOutputStream();
	        ObjectOutputStream objOutStream = new ObjectOutputStream(baOutStream);
	        objOutStream.writeObject(obj);
	        objOutStream.close();

	        ba = baOutStream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return ba;
	}

	// Retourne un objet de la classe donnée représenté par le tableau d'octets
    public static <T> T byteArrayToObject(byte[] ba, Class<T> cl) {
    	Object desObj = null;
		try {
	        ByteArrayInputStream baInStream = new ByteArrayInputStream(ba);
	        ObjectInputStream objInStream = new ObjectInputStream(baInStream);
	        desObj = objInStream.readObject();
	        objInStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

        return cl.cast(desObj);
    }
}
