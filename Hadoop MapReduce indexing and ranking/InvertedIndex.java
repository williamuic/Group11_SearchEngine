import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;


public class InvertedIndex {


    public static class TokenizerMapper extends Mapper<Object, Text, Text, Text>{

        private Text word = new Text();

        private String pattern = "[^a-zA-Z]";  //only find key with 26 English characters

        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {


            // get file name
            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            String line = value.toString();

            StringTokenizer tk = new StringTokenizer(line);

            // replace punctuations with space, only keep english words defined within the pattern
            line = line.replaceAll(pattern, " ");


            line = line.toLowerCase();


            StringTokenizer itr = new StringTokenizer(line);

            while (itr.hasMoreTokens()) {
                word.set(itr.nextToken());
                context.write(word, new Text(fileSplit.getPath().getName()));
            }
        }
    }

    public static class IntSumReducer extends Reducer<Text,Text,Text,Text> {


        public void reduce(Text keyword, Iterable<Text> docIDList, Context output)
                throws IOException, InterruptedException {

            HashMap<String,Integer> hashtable = new HashMap<String,Integer>();
            Iterator<Text> itr = docIDList.iterator();
            int count = 0;
            String docID;

            while (itr.hasNext()) {
                docID = itr.next().toString();

                if(hashtable.containsKey(docID)){
                    count = (hashtable.get(docID));
                    count += 1;
                    hashtable.put(docID, count);
                }else{
                    hashtable.put(docID, 1);
                }
            }

            //todo sort it for ranking
            StringBuffer buf = new StringBuffer("");
            for(Map.Entry<String, Integer> h: hashtable.entrySet())
                buf.append(h.getKey() + ":" + h.getValue() + "\t");


            Text optext = new Text(buf.toString());
            output.write(keyword, optext);

        }
    }


    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        Configuration conf = new Configuration();
        FileSystem hdfs = FileSystem.get(conf);

        Job job = Job.getInstance(conf, "Inverted Index");

        job.setJarByClass(InvertedIndex.class);
        job.setMapperClass(TokenizerMapper.class);

        // todo set Combiner class
        //job.setCombinerClass(IntSumReducer.class);

        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        Path outpath = new Path(args[1]);
        if(hdfs.exists(outpath)){
            hdfs.delete(outpath, true);
        }
        FileOutputFormat.setOutputPath(job, outpath);
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
