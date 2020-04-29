package juicer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ObjWriter{
	private Geometry geo = null;
	
	private int numVerts = 1;
	private boolean written = false;
	private Integer obj = 0;
	
	public ObjWriter(Geometry geo) {
		this.geo = geo;
		
	}
	
	public void saveFile(String fileName) {		
		try {
	            FileWriter writer = new FileWriter(fileName, written);
	            
	            if(geo != null) {
	    			
	            	Map<Integer, Integer> iMap = new HashMap<Integer, Integer>();
	            	
	            	writer.write("o " + obj.toString() + "\n"); obj++;
	            	
	            	int cnt = numVerts;
	            	for(int z = 0; z < geo.DEPTH; ++z) {
	        	        for(int y = 0; y < geo.HEIGHT; ++y) {
	        	        	for(int x = 0; x < geo.WIDTH; ++x) {
	        	        		if(geo.subRegionVisited.contains(geo.XYZ(x,y,z))) {
	        	        			writer.write("v " + x + " "+ y + " " + z + "\n");
	        	        			iMap.put(geo.XYZ(x,y,z), cnt);
	        	        			cnt++;
	        	        		}
	        	        	}
	        	        }
	                }
	            	numVerts = cnt;
	            	
	            	writer.write("\n");
	            	writer.write("l ");
	            
    				for(int j : (ArrayList<Integer>)(geo.shapesIndices.get(geo.shapesIndices.size()-1))) {
    					if(iMap.get(j) != null) {
    						writer.write(iMap.get(j).toString() + " ");
    					}
    				}
    				
    				writer.write("\n\n");
	    		}
	            writer.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }		
		written = true;
		//System.out.println(obj.toString());
	}
	
}