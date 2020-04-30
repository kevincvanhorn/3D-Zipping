package juicer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ObjWriter{
	private Geometry geo = null; // Geometry Instance
	
	private int numVerts = 1; 	     // Vertex count for OBJ
	private boolean written = false; // Tracks if the file should be appended to
	private Integer obj = 0;	     // Object count for OBJ
	
	// Constructor
	public ObjWriter(Geometry geo) {
		this.geo = geo;
	}
	
	/**
	 * Write to Wavefront OBJ from the active Geometry instance.
	 * @param fileName the name of the output file.
	 */
	public void saveFile(String fileName) {		
		try {
	            FileWriter writer = new FileWriter(fileName, written);
	            
	            if(geo != null) {
	    			
	            	Map<Integer, Integer> iMap = new HashMap<Integer, Integer>();
	            	
	            	writer.write("o " + obj.toString() + "\n"); obj++;
	            	
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
	}
	
	// Mapping from index directions from halfList to 3D Vectors
	protected Geometry.Vector3D vMap[] = new Geometry.Vector3D[]{
		new Geometry.Vector3D(0,0,0),           //-1
		new Geometry.Vector3D(0.5f,0,.5f),      // 0
		new Geometry.Vector3D(0.5f,0.5f,.5f),   // 1
		new Geometry.Vector3D(0,0.5f,.5f),      // 2
		new Geometry.Vector3D(-0.5f,0.5f,.5f),  // 3
		new Geometry.Vector3D(-0.5f,0,.5f),     // 4
		new Geometry.Vector3D(-0.5f,-0.5f,.5f), // 5
		new Geometry.Vector3D(0,-0.5f,.5f),     // 6
		new Geometry.Vector3D(0.5f,-0.5f,.5f),  // 7
		new Geometry.Vector3D(0,0,.5f),         // 8
	};
	
}