package rambris.cloudprice;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;

public class AWSFetcher
{
	protected String[] uris;
	protected MongoClient mongoClient=new MongoClient();
	protected MongoDatabase db=null;
	protected MongoCollection<Document> instances;
	
	public static String HTTPGet(String url) throws IOException, IOException
	{
		URLConnection conn = new URL(url).openConnection();
		conn.setRequestProperty("Accept-Charset", "UTF-8");
		InputStream in=conn.getInputStream();
		StringWriter str=new StringWriter();
		IOUtils.copy(in,str,"UTF-8");
		return str.toString();
	}

	public AWSFetcher()
	{
	}
	
	public void init() throws IOException
	{
		db = mongoClient.getDatabase("cloudprice");
		instances = db.getCollection("instances");
		uris=fetchURIsToJSONs();		
		fetchPrices();
		
	}
	
	private void updateInstance(Document instance)
	{
		Bson key=and(eq("baseName", instance.get("baseName")), eq("region", instance.get("region")), eq("size", instance.get("size")));
		instances.updateOne(key, new Document("$set", instance), new UpdateOptions().upsert(true));
	}
	
	public void fetchPrices() throws IOException
	{
		for(String search:new String[]{".*linux-od.*",".*mswin-od.*",".*mswinSQL-od.*",".*mswinSQLEnterprise-od.*"})
		{
			String uri=findURI(search);
			if(uri!=null)
			{
				System.out.println(uri);
				JsonElement json=fetchCallback(uri);
				for(Document instance:parseInstances(json))
				{
					updateInstance(instance);
				}
			}			
		}
	}
	
	public String findURI(String search)
	{
		Pattern p=Pattern.compile(search);
		for(String uri:uris)
		{
			Matcher m=p.matcher(uri);
			if(m.matches()) return uri;
		}
		return null;
	}

	public List<Document> parseInstances(JsonElement root) throws IOException
	{
		List<Document> retVal=new LinkedList<Document>();
		JsonArray regions=root.getAsJsonObject().getAsJsonObject("config").get("regions").getAsJsonArray();
		for(JsonElement regelement:regions)
		{
			JsonObject region=regelement.getAsJsonObject();
			String regionName=region.get("region").getAsString();
			for(JsonElement typeElement:region.get("instanceTypes").getAsJsonArray())
			{
				JsonObject typeObject=typeElement.getAsJsonObject();
				String instanceType=typeObject.get("type").getAsString();
				for(JsonElement sizeElement:typeObject.get("sizes").getAsJsonArray())
				{
					JsonObject sizeObject=sizeElement.getAsJsonObject();
					Document i=new Document();
					i.put("baseName", sizeObject.get("valueColumns").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString());
					i.put("region", regionName);
					i.put("instanceType", instanceType);
					i.put("size", sizeObject.get("size").getAsString());
					i.put("vCPU", Integer.parseInt(sizeObject.get("vCPU").getAsString()));
					i.put("memoryGiB", Float.parseFloat(sizeObject.get("memoryGiB").getAsString()));
					BigDecimal price=new BigDecimal(sizeObject.get("valueColumns").getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("prices").get("USD").getAsString());
					price=price.multiply(new BigDecimal(10000));
					i.put("price", price.intValue());
					retVal.add(i);
				}
			}
		}
		return retVal;
	}
	
	public class InstanceInfo
	{
		String region;
		String name;
		int vCPU;
		int memory;
		String os;
		BigDecimal price;
	}
	
	private static Pattern callbackPattern=Pattern.compile("callback\\((.*)\\);");
	private static Pattern keyPattern=Pattern.compile("(\\w+):");
	public JsonElement fetchCallback(String uri) throws IOException
	{
		String page=HTTPGet(uri);
		Matcher m=callbackPattern.matcher(page);
		StringBuffer s=new StringBuffer();
		if(m.find())
		{
			String js=m.group(1);
			Matcher m2=keyPattern.matcher(js);
			while(m2.find())
			{
				m2.appendReplacement(s, "\""+m2.group(1)+"\":");
			}
			m2.appendTail(s);
			JsonElement e=new JsonParser().parse(s.toString());
			return e;
		}
		return null;
	}
	
	private static Pattern modelPattern=Pattern.compile("model\\s*:\\s*'(.*pricing.*)'", Pattern.MULTILINE);
	public String[] fetchURIsToJSONs() throws IOException
	{
		List<String> uris=new ArrayList<String>();
		String page=HTTPGet("http://aws.amazon.com/ec2/pricing/");
		Matcher m=modelPattern.matcher(page);
		while(m.find())
		{
			if(m.group(1).startsWith("//")) uris.add("http:"+m.group(1));
			else uris.add(m.group(1));
		}
		return uris.toArray(new String[0]);
	}
}
