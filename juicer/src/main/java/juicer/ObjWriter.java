package juicer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ObjWriter{
	private Geometry geo = null;
	
	private boolean written = false;
	
	public ObjWriter(Geometry geo) {
		this.geo = geo;
		
	}
	
	public void saveFile(String fileName) {
		if(written) {return;}
		
		try {
	            FileWriter writer = new FileWriter(fileName, false);
	            
	            if(geo != null) {
	    			
	            	Map<Integer, Integer> iMap = new HashMap<Integer, Integer>();
	            	
	            	int cnt = 1;
	            	for(int z = 0; z < geo.DEPTH; ++z) {
	        	        for(int y = 0; y < geo.HEIGHT; ++y) {
	        	        	for(int x = 0; x < geo.WIDTH; ++x) {
	        	        		if(geo.curRegion.contains(geo.XYZ(x,y,z))) {
	        	        			writer.write("v " + x + " "+ y + " " + z + "\n");
	        	        			iMap.put(geo.XYZ(x,y,z), cnt);
	        	        			cnt++;
	        	        		}
	        	        	}
	        	        }
	                }
	            	
	            	writer.write("\n");
	            	writer.write("l ");
	            
	    			//for(int i = 0; i < geo.shapesIndices.size(); ++i) {
	    				for(int j : (ArrayList<Integer>)(geo.shapesIndices.get(0))) {
	    					//Geometry.Vector3D v =  geo.vFromIndex3D(j);
	    					if(iMap.get(j) != null) {
	    						System.out.println(iMap.get(j) + " | " + j);
	    						writer.write(iMap.get(j).toString() + " ");
	    					}
	    				}
	    			//}
	    		}
	            writer.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }		
		written = true;
	}
	
}