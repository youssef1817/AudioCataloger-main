package com.maknoon;

import java.io.*;
import java.sql.*;

// Used for golden version (i.e. with DVDs). DVDNumber column is just a temp one, rename it to CDNumber once it is done.
public class MSAccess
{
    public static void main(String[] args){new MSAccess();}
    private MSAccess()
    {
        try
        {
            Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
            final Connection con = DriverManager.getConnection("jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=E:\\Audios\\index-17.1.mdb;DriverID=22;READONLY=false}");
            final Statement updateStmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            final Statement stmt = con.createStatement();
            final ResultSet rs = stmt.executeQuery("SELECT * FROM Chapters ORDER BY Code");
            int DVDNumber = 1;
            long totalSize=0;
            while(rs.next())
            {
                final File source = new File("E:\\Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm");

                if((source.length()+totalSize)>=4700372992L)
                {
                    DVDNumber++;
                    totalSize=0;
                }
                totalSize = totalSize+source.length();

                // Not working
                //rs.moveToCurrentRow();
                //rs.updateString("DVDNumber","الإسطوانة-"+DVDNumber);
                //rs.updateRow();
                
                updateStmt.executeUpdate("UPDATE Chapters SET DVDNumber='DVD-"+DVDNumber+"' WHERE Code="+rs.getInt("Code"));
                // Insert/Update Arabic in MS Access using Java is not possible [http://www.thatsjava.com/java-desktop/16008/]
            }
            con.close();
        }
        catch(Exception e){e.printStackTrace();}
    }
}
