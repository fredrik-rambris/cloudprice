package rambris.cloudprice;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException
    {
        System.out.println( "Hello World!" );
        AWSFetcher f=new AWSFetcher();
        f.init();
        f.getSomething();
    }
}
