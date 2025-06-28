package com.maknoon;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConvertToNewExportFile
{
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(new Runnable(){public void run()
		{
			try
			{
				final JFileChooser fc = new JFileChooser();
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				fc.setFileFilter(new FileNameExtensionFilter("acdb", "acdb"));

				final int returnVal = fc.showOpenDialog(null);
				if(returnVal==JFileChooser.APPROVE_OPTION)
				{
					final File file = fc.getSelectedFile();

					final Path tempDir = Files.createTempDirectory("audiocataloger");

					final TarInputStream tarFile = new TarInputStream(new FileInputStream(file), "UTF-8");
					for (TarEntry tarEntry = tarFile.getNextEntry(); tarEntry != null; tarEntry = tarFile.getNextEntry())
					{
						if (tarEntry.getName().equals("exported_db"))
						{
							tarFile.copyEntryContents(new FileOutputStream(tempDir + "/exported_db_temp"));

							final javax.crypto.spec.DESKeySpec pbeSpec = new javax.crypto.spec.DESKeySpec(new byte[] {(byte) 0x7d, (byte) 0x66, (byte) 0x43, (byte) 0x5f, (byte) 0x1a, (byte)0xe9, (byte)0xe0, (byte)0xae});
							final DesEncrypter dencrypter = new DesEncrypter(javax.crypto.SecretKeyFactory.getInstance("DES").generateSecret(pbeSpec));
							dencrypter.decrypt(new FileInputStream(tempDir + "/exported_db_temp"), new FileOutputStream(tempDir + "/exported_db"));
							break;
						}
					}

					Files.writeString(new File(tempDir + "/info").toPath(), "converted\n4.6\nconverted from old encrypted file");

					// Tar all files
					byte[] buf = new byte[1024];
					int len;

					final TarOutputStream tarOutput = new TarOutputStream(new FileOutputStream(fc.getSelectedFile().getAbsolutePath() + ".tar"), "UTF-8");
					tarOutput.setLongFileMode(TarOutputStream.LONGFILE_GNU);

					TarEntry tarEntry = new TarEntry("info");
					tarEntry.setSize(new File(tempDir + "/info").length());
					tarOutput.putNextEntry(tarEntry);
					FileInputStream in = new FileInputStream(tempDir + "/info");
					while ((len = in.read(buf)) > 0) tarOutput.write(buf, 0, len);
					tarOutput.closeEntry();
					in.close();

					tarEntry = new TarEntry("exported_db");
					tarEntry.setSize(new File(tempDir + "/exported_db").length());
					tarOutput.putNextEntry(tarEntry);
					in = new FileInputStream(tempDir + "/exported_db");
					while ((len = in.read(buf)) > 0) tarOutput.write(buf, 0, len);
					tarOutput.closeEntry();
					in.close();
					tarOutput.close();
				}
			}
			catch(Exception e){e.printStackTrace();}
		}});
	}
}