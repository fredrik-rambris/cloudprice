package rambris.cloudprice;

import static com.mongodb.client.model.Filters.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Hello world!
 *
 */
public class App implements Closeable
{
	protected MongoClient mongoClient;
	protected MongoDatabase db;
	protected MongoCollection<Document> instances;
	protected MongoCollection<Document> storage;
	
	public App()
	{
		mongoClient=new MongoClient();
		db = mongoClient.getDatabase("cloudprice");
		instances = db.getCollection("instances");
		storage = db.getCollection("storage");
	}
	
    public static void main( String[] args ) throws IOException, ParseException
    {
    	Logger.getLogger("org.mongodb.driver").setLevel(Level.SEVERE); 
    	
    	Options opts=new Options();
    	opts.addOption("c", "cpu", true, "Number of vCPU");
    	opts.addOption("r", "ram", true, "GiB of RAM");
    	opts.addOption("u", "update", false, "Update prices");
    	opts.addOption("r", "region", true, "Region");
    	opts.addOption("t", "type", true, "Type of instance");
    	opts.addOption(null, "list-types", false, "Show list of types");
    	opts.addOption(null, "list-regions", false, "Show list of Regions");
    	opts.addOption(null, "help", false, "Show this help");

    	BasicParser parser=new BasicParser();
    	CommandLine cline=parser.parse(opts, args);
    	
    	if(cline.hasOption("help"))
    	{
    		HelpFormatter help=new HelpFormatter();
    		help.printHelp("cloudprice [OPTIONS]", opts);
    	}
    	if(cline.hasOption("update"))
    	{
    		try(AWSFetcher fetcher=new AWSFetcher())
    		{
    			fetcher.fetchPrices();
    		}
    	}
    	if(cline.hasOption("list-types"))
    	{
    		try(App app=new App())
    		{
    			app.listTypes();
    		}
    	}
    	else if(cline.hasOption("list-regions"))
    	{
    		try(App app=new App())
    		{
    			app.listRegions();
    		}    		
    	}
    	else
    	{
    		int cpu=0, ram=0;
    		if(cline.hasOption("cpu"))
    		{
    			cpu=Integer.parseInt(cline.getOptionValue("cpu"));
    		}
    		if(cline.hasOption("ram"))
    		{
    			ram=Integer.parseInt(cline.getOptionValue("ram"));
    		}
    		String region=cline.getOptionValue("region");
    		String type=cline.getOptionValue("type");

    		try(App app=new App())
    		{
    			app.listInstances(region, type, cpu, ram);
    		}
    	}
    }
    
    private void listInstances(String region, String type, int cpu, int ram)
	{
		System.out.println("Region          | Class                | Type         | vCPU | RAM   | Price");
		System.out.println("----------------+----------------------+--------------+------+-------+------");
		Vector<Bson> filters=new Vector<Bson>();
		if(region!=null) filters.add(eq("region", region));
		if(type!=null) filters.add(eq("baseName", type));
		if(cpu>0) filters.add(gt("vCPU", cpu));
		if(ram>0) filters.add(gte("memoryGiB", ram));

		FindIterable<Document>result;

		if(filters.size()==0)
		{
			result=instances.find().sort(new Document("price", 1));
		}
		else
		{
			result=instances.find(and(filters)).sort(new Document("price", 1));
		}
		
		/* and(eq("region", region), eq("baseName", type)) */
    	for(Document doc:result)
    	{
    		System.out.printf("%-15s | %-20s | %-12s | %4d | %5.1f | %.03f%n", doc.get("region"), doc.get("baseName"), doc.get("size"), doc.get("vCPU"), doc.get("memoryGiB"), doc.getInteger("price")/10000.0f);
    	}
	}

	private void listTypes()
    {
    	for(String name:instances.distinct("baseName", String.class))
    	{
    		System.out.println(name);
    	}
    }

    private void listRegions()
    {
    	for(String region:instances.distinct("region", String.class))
    	{
    		System.out.println(region);
    	}
    }
    
	@Override
	public void close() throws IOException
	{
		mongoClient.close();
	}
}
