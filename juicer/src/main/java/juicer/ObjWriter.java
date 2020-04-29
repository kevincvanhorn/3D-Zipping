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
	            	
	            	/*int cnt = numVerts;
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
	            	numVerts = cnt;*/
	            	
	            	//writer.write("\n");
	            	//writer.write("l ");
	            
    				/*for(int j : (ArrayList<Integer>)(geo.shapesIndices.get(geo.shapesIndices.size()-1))) {
    					
    					if(iMap.get(j) != null) {
    						writer.write(iMap.get(j).toString() + " ");
    					}
    				}*/
	            	ArrayList<Integer> indices = ((ArrayList<Integer>)(geo.shapesIndices.get(geo.shapesIndices.size()-1)));
	            	ArrayList<Byte> halfList = ((ArrayList<Byte>)(geo.halfLists.get(geo.halfLists.size()-1)));
	            	for(int j = 0; j < indices.size(); ++j) {
	            		Geometry.Vector3D v = geo.vFromIndex3D(indices.get(j));
	            		v.plusEq(vMap[halfList.get(j) + 1]);
	            		writer.write("v " + v.x + " "+ v.y + " " + v.z + "\n");
    				}
	            	
	            	writer.write("\n");
	            	writer.write("l ");
	            	
	            	int cnt = numVerts;
	            	for(int j = 0; j < indices.size(); ++j) {
	            		
	            		/*if(j > 0 && Math.abs(indices.get(j) - indices.get(j-1)) > geo.XY+1) {
	            			writer.write("\n");
	    	            	writer.write("l ");
	            		}*/
	            		
	            		writer.write(cnt + " "); cnt++;
    				}
	            	numVerts = cnt;
    				
    				writer.write("\n\n");
	    		}
	            writer.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }		
		written = true;
		//System.out.println(obj.toString());
	}
	
	protected Geometry.Vector3D vMap[] = new Geometry.Vector3D[]{
		new Geometry.Vector3D(0,0,0),   //-1
		new Geometry.Vector3D(0.5f,0,.5f), // 0
		new Geometry.Vector3D(0.5f,0.5f,.5f), // 1
		new Geometry.Vector3D(0,0.5f,.5f), // 2
		new Geometry.Vector3D(-0.5f,0.5f,.5f), // 3
		new Geometry.Vector3D(-0.5f,0,.5f), // 4
		new Geometry.Vector3D(-0.5f,-0.5f,.5f), // 5
		new Geometry.Vector3D(0,-0.5f,.5f), // 6
		new Geometry.Vector3D(0.5f,-0.5f,.5f), // 7
		new Geometry.Vector3D(0,0,.5f), // 8
	};
	
}