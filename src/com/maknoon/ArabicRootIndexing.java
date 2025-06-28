package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;

class ArabicRootIndexing
{
	private ArabicRootIndexing()
    {
    	try
    	{
			final IndexWriterConfig conf = new IndexWriterConfig(new ArabicRootAnalyzer());
			conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
	    	final IndexWriter indexWriter = new IndexWriter(org.apache.lucene.store.FSDirectory.open(new File(cl.getResource("rootsTableIndex").toURI()).toPath()), conf);
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("E:/AudioCataloger Media/MS Access DB/test.txt"), StandardCharsets.UTF_8));
			while(in.ready())
			{
				final StringTokenizer tokens = new StringTokenizer(in.readLine(), ",");
				final Document doc = new Document();
				doc.add(new Field("word", tokens.nextToken(), StringField.TYPE_STORED));
				doc.add(new Field("root", tokens.nextToken(), StringField.TYPE_STORED));
		        indexWriter.addDocument(doc);
			}
			
			in.close();
			indexWriter.close();
    	}
		catch(Exception e){e.printStackTrace();}
    }

	static ClassLoader cl;
	public static void main (String[] arg)
	{
		cl = ArabicRootIndexing.class.getClassLoader();
		new ArabicRootIndexing();
	}
}