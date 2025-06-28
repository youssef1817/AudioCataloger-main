package com.maknoon;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.File;

public class CreateEmptyIndexFile
{
	public static void main(String[] args){new CreateEmptyIndexFile();}
	private CreateEmptyIndexFile()
	{
		final ClassLoader cl = CreateEmptyIndexFile.class.getClassLoader();
		try
		{
			final IndexWriterConfig conf = new IndexWriterConfig(new ArabicAnalyzer());
			final IndexWriterConfig rootsConf = new IndexWriterConfig(new ArabicRootAnalyzer());
			final IndexWriterConfig luceneConf = new IndexWriterConfig(new org.apache.lucene.analysis.ar.ArabicAnalyzer());

			conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			rootsConf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			luceneConf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

			final IndexWriter indexWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("index").toURI()).toPath()), conf);
			final IndexWriter rootsWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("rootsIndex").toURI()).toPath()), rootsConf);
			final IndexWriter luceneWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("luceneIndex").toURI()).toPath()), luceneConf);

			indexWriter.close();
            rootsWriter.close();
            luceneWriter.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}
