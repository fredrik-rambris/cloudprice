package rambris.cloudprice;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class AWSFetcher
{
	protected String[] uris;
	
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
		uris=fetchURIsToJSONs();		
	}

	public void getSomething() throws IOException
	{
		JsonElement root=fetchCallback(uris[0]);
		String baseName=FilenameUtils.getBaseName(FilenameUtils.getBaseName(uris[0]));
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
					Map<String,Object> i=new HashMap<String,Object>();
					i.put("baseName", baseName);
					i.put("region", regionName);
					i.put("instanceType", instanceType);
					i.put("size", sizeObject.get("size").getAsString());
					i.put("vCPU", Integer.parseInt(sizeObject.get("vCPU").getAsString()));
					i.put("memoryGiB", Float.parseFloat(sizeObject.get("memoryGiB").getAsString()));
					i.put("price", new BigDecimal(sizeObject.get("valueColumns").getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("prices").get("USD").getAsString()));
					System.out.println(new Gson().toJson(i));
				}
			}
		}
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
