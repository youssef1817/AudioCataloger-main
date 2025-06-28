package com.maknoon;

import java.sql.*;
import java.util.Vector;

// To set the Contents.Seq, ContentCat
public class MSAccess_externalDB
{
    public static void main(String[] args){new MSAccess_externalDB();}
    private MSAccess_externalDB()
    {
        try
        {
            //Class.forName("sun.jdbc.odbc.JdbcOdbcDriver"); // Version 3.2, Not working in Java 8, ucanaccess is a replacement

            final Connection con = DriverManager.getConnection("jdbc:ucanaccess://C:/Users/Ibrahim/Desktop/Test/index-18-mag.mdb;DriverID=22;READONLY=false}");
            final Statement updateStmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            final Statement stmt1 = con.createStatement();
	        final Statement stmt2 = con.createStatement();

	        // Construct the feqh tree with category_id and the path as string. Then compare it directly with pathtree.
	        final Vector<String> pathtree = new Vector<>(1196);

	        try
	        {
		        final ResultSet rs2 = stmt2.executeQuery("SELECT * FROM Category ORDER BY Category_id");
		        while(rs2.next())
		        {
			        final int Category_parent = rs2.getInt("Category_parent");
			        if(Category_parent==0)
				        pathtree.add(rs2.getInt("Category_id")-1, rs2.getString("Category_name"));
			        else
				        pathtree.add(rs2.getInt("Category_id")-1, pathtree.elementAt(Category_parent-1)+"\\"+rs2.getString("Category_name"));
		        }
	        }
	        catch(Exception e){e.printStackTrace();}

            final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Contents ORDER BY Code, Offset");
            int seq = 1, prev_code=0;
            while(rs1.next())
            {
	            int code = rs1.getInt("Code");
	            if(code!=prev_code)
	            {
		            prev_code = code;
		            seq=1;
	            }

                updateStmt.executeUpdate("UPDATE Contents SET Seq="+seq+" WHERE idd="+rs1.getInt("idd"));

	            int category_id = pathtree.indexOf(rs1.getString("pathtree"))+1;
	            if(category_id!=0) // To avoid empty categorized indexes
	                updateStmt.executeUpdate("INSERT INTO ContentCat VALUES ("+code+","+seq+","+category_id+","+rs1.getInt("Sheekh_id")+","+rs1.getInt("Book_id")+")");
	            seq++;
            }
            con.close();
        }
        catch(Exception e){e.printStackTrace();}
    }
}
