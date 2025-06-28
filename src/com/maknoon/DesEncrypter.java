package com.maknoon;

import javax.crypto.*;
import java.io.*;
import javax.crypto.spec.IvParameterSpec;

// This class will be used to encrypt/decrypt the exported db file
class DesEncrypter
{
	private Cipher ecipher, dcipher;
	DesEncrypter(SecretKey key)
	{
		// Create an 8-byte initialization vector
		final byte[] iv = new byte[]{(byte)0x8E, (byte) 0x12, (byte) 0x39, (byte)0x9C, (byte) 0x07, (byte) 0x72, (byte) 0x6F, (byte) 0x5A};
		final IvParameterSpec paramSpec = new IvParameterSpec(iv);

		try
		{
			ecipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
			dcipher = Cipher.getInstance("DES/CBC/PKCS5Padding");

			// CBC requires an initialization vector
			ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
			dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
		}
		catch(Exception e){e.printStackTrace();}
	}

	// Buffer used to transport the bytes from one stream to another
	final private byte[] buf = new byte[1024];
	void encrypt(InputStream in, OutputStream out)
	{
		try
		{
			// Bytes written to out will be encrypted
			out = new CipherOutputStream(out, ecipher);

			// Read in the cleartext bytes and write to out to encrypt
			int numRead;
			while ((numRead = in.read(buf)) >= 0)
				out.write(buf, 0, numRead);
			out.close();
			in.close();
		}
        catch(Exception e){e.printStackTrace();}
	}

	void decrypt(InputStream in, OutputStream out)
	{
		try
		{
			// Bytes read from 'in' will be decrypted
			in = new CipherInputStream(in, dcipher);

			// Read in the decrypted bytes and write the cleartext to out
			int numRead;
			while((numRead = in.read(buf)) >= 0)
				out.write(buf, 0, numRead);
			out.close();
			in.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}