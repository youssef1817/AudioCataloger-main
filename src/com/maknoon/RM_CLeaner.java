package com.maknoon;

import java.io.*;
import java.nio.file.Files;
import java.sql.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

// Reference: http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html?page=4
class StreamGobbler extends Thread
{
    InputStream is;
    String type;
    OutputStream os;

    StreamGobbler(InputStream is, String type, OutputStream redirect)
    {
        this.is = is;
        this.type = type;
        this.os = redirect;
    }

    public void run()
    {
        try
        {
            final PrintWriter pw = new PrintWriter(os);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null)
            {
                pw.println(line);
                //System.out.println(type + ">" + line);
            }
            pw.flush();
        }
        catch(IOException ioe){ioe.printStackTrace();}
    }
}

public class RM_CLeaner
{
    public static void main(String[] args)
    {
        try
        {
            Class.forName("org.h2.Driver");
		    final Connection con = DriverManager.getConnection("jdbc:h2:db/audioCatalogerDatabase");
            final Statement stmt = con.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT Path, FileName, Code FROM Chapters ORDER BY Code");
            final FileOutputStream fos = new FileOutputStream("RM_Cleaner.txt");
            int DVDNumber = 1;
            long totalSize=0;
            while(rs.next())
            {
                final File source = new File("E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm");

                if((source.length()+totalSize)>=4700372992L) // 4700372992 bytes, DVD writable size [http://club.myce.com/f33/actual-size-dvd-r-4-7gb-139256/]
                {
                    DVDNumber++;
                    totalSize=0;
                }
                final File folder = new File("E:\\DVD_"+DVDNumber+"\\"+rs.getString("Path"));
                folder.mkdirs();

                //new CopyFile("E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm", "E:\\Audios_"+DVDNumber+"\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm");
                //rmeditor.exe -i <input> -o <output> -t "" -a "" -c "" -C "" -q "" -n ""  -R 0
                //http://service.real.com/help/library/guides/RealProducer10/htmfiles/editing.htm
				//http://lists.helixcommunity.org/pipermail/helix-producer-issues/2004-December/007043.html
                final Process proc = Runtime.getRuntime().exec(new String[]{"E:\\RMEditor\\rmeditor.exe", "-i", "E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm", "-o", "E:\\DVD_"+DVDNumber+"\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm", "-t", "\"\"", "-a", "\"\"", "-c", "\"\"", "-C", "\"\"", "-q", "\"\"", "-n", "\"\"", "-R", "0"});

                StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR", fos); // Any error message?
                StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT", fos); // Any output?

                // kick them off
                errorGobbler.start();
                outputGobbler.start();

                // Any error???
                int exitVal = proc.waitFor();
                if(exitVal==1) // Copy the file normally
                {
                    System.out.println("Code: "+rs.getString("Code")+"   ExitValue: " + exitVal+"   File: "+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm DVD: "+DVDNumber);
                    //new CopyFile("E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm", "E:\\DVD_"+DVDNumber+"\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm");
					Files.copy(new File("E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm").toPath(), new File("E:\\DVD_"+DVDNumber+"\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm").toPath(), REPLACE_EXISTING);
                }
                totalSize = totalSize+source.length();
                fos.flush();
            }
            fos.close();
            con.close();
        }
        catch(Throwable t){t.printStackTrace();}
    }
}
