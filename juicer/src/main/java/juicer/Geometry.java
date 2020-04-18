package juicer;

// https://github.com/imagej/tutorials/blob/master/maven-projects/using-ops/src/main/java/UsingOpsLabeling.java

import net.imagej.mesh.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import sc.iview.SciView;

import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ImageProcessor;
import net.imagej.ops.labeling.cca.DefaultCCA;
import java.util.Stack;

@Plugin(type = Command.class)
public class Geometry<T extends RealType<T>> implements Command{

    private OpService opService;
    private ConvertService convertService;
    private ImgPlus image;
    private ImagePlus imp;
    
    public int WIDTH;
    private float WIDTH_INV;
    public int HEIGHT;
    public int DEPTH;
    private int XY; // WIDTH * HEIGHT
    
    private byte V_ISOLATED = 8; // No vertices in the 8 connected planar region of a vertex.
    
    //public Set<Vector3D> Regions;
    public int XYZ(int x, int y, int z) { return x + WIDTH*(y+ HEIGHT*z); }
    private int[] Adjacents;
    private ObjWriter objWriter;
    
    public Vector3D vFromIndex3D(int idx) {
    	// x + Wy + WHz
		int x = idx%WIDTH;
		int y = ((idx - x)/WIDTH) % HEIGHT;// (i - x)
		int z = (idx-x - WIDTH*y) / XY;
    	return new Vector3D(x,y,z);
	}
    
    //public Vector2D V2FromValue(int val) { 
    //	return new Vector2D((int)(val%WIDTH), (int)(((val)% WIDTH)*WIDTH_INV)); 
	//}

   protected VisitedSet visitedSet;
   
   private class VisitedSet {
    	private byte[] set;

		public VisitedSet(int size) {
			set = new byte[size];
			CalcEdges();
		}
    	
    	// True if the right-most bit is 1
    	public boolean contains(int idx) {
    		return (set[idx] &0x1) == 1;
    	}
    	
    	// 2nd bit indicates if this pixel is at any extent of the image
    	public boolean onEdge(int idx) {
    		return (set[idx] & 0x2) >> 1 == 1;
    	}
    	
    	// Bits 3,4,5 = XY direction index for edge pixels
    	// Includes diagonals as corners
    	public int getEdgeIndex(int idx) {
    		return (set[idx] & 0x1C) >>2;
    	}
    	
    	// Set the right-most bit to 1
    	public void put(int idx) {
    		set[idx] |= 0x1;
    	}
    	
    	private void CalcEdges() {
    		for(int z = 0; z < DEPTH; ++z) {
    			// Right edge = 0
    			for(int y = 0; y < HEIGHT; y++) {
    	        	set[XYZ(WIDTH-1,y,z)] |= 0x2;
    	        }
    			// Top edge = 2
    			for(int x = 0; x < WIDTH; x++) {
	        		set[XYZ(x,0,z)] |= 0x2;
	        		set[XYZ(x,0,z)] |= 0x8; // [010]00
    			}
    			// Left edge = 4
    			for(int y = 0; y < HEIGHT; y++) {
    	        	set[XYZ(0,y,z)] |= 0x2;
    	        	set[XYZ(0,y,z)] |= 0x10; // [100]00
    	        }
    			// Bot edge = 6
	        	for(int x = 0; x < WIDTH; x++) {
	        		set[XYZ(x,HEIGHT-1,z)] |= 0x2;
	        		set[XYZ(x,HEIGHT-1,z)] |= 0x18; // [110]00
	        	}
	        	
	        	// Set XY Corners
	        	set[XYZ(WIDTH-1,0,z)] = 0x6; // TR Corner = 1 -> // [001]10
	        	set[XYZ(0,0,z)] = 0xE; // TL Corner = 3 -> // [011]10
	        	set[XYZ(0,HEIGHT-1,z)] = 0x16; // BL Corner = 5 -> // [101]10
	        	set[XYZ(WIDTH-1,HEIGHT-1,z)] = 0x1E; // BR Corner = 7 -> // [111]10
    		}
    	}
    }
    
	public static class Vector3D{
		public int x, y, z;
		
		public Vector3D(int x, int y, int z) {
			this.x = x;
			this.y = y; 
			this.z = z;
		}
		
		@Override
		public int hashCode() {
			return ((x* 73856093)^(y* 83492791)^(z* 83442791));
		}
		
		@Override
	    public boolean equals(Object obj) {
	        if (this == obj) return true;
	        if (!(obj instanceof Geometry.Vector3D)) return false;
	        Geometry.Vector3D it = (Geometry.Vector3D) obj;
	        return x == it.x && y == it.y && it.z == z;
	    }
		
		 public Vector3D clone() {
	        return new Vector3D(x, y, z);
	    }
		 
		 @Override
		    public String toString() {
		        return "{" + x + ", " + y + "}";
		    }
	}
	
	public static class Vector2D{
		public int x, y;
		
		public Vector2D(int x, int y) {
			this.x = x;
			this.y = y; 
		}
		
		@Override
		public int hashCode() {
			return ((x* 73856093)^(y* 83492791));
		}
		
		@Override
	    public boolean equals(Object obj) {
	        if (this == obj) return true;
	        if (!(obj instanceof Geometry.Vector3D)) return false;
	        Geometry.Vector3D it = (Geometry.Vector3D) obj;
	        return x == it.x && y == it.y;
	    }
		
		 public Vector2D clone() {
	        return new Vector2D(x, y);
	    }
		 
		 @Override
		    public String toString() {
		        return "{" + x + ", " + y + "}";
		    }
	}
	
	public Geometry(OpService opService, ImgPlus image, ConvertService convertService) {
		this.opService = opService;
		this.image = image;
		this.convertService = convertService;
		imp = ImageJFunctions.wrap(image, image.getName());
		WIDTH = imp.getWidth();
		WIDTH_INV = (float)1 / (float)WIDTH;
		HEIGHT = imp.getHeight();
		DEPTH = imp.getStackSize();
		XY = WIDTH*HEIGHT;
		
		visitedSet = new VisitedSet(WIDTH * HEIGHT * DEPTH);
		
		// LOCAL
		//curRegion = new HashSet<Vector3D>();
		curRegion = new HashSet<Integer>();
		
		Adjacents = new int[]{ 
				1, -1*(WIDTH-1), -1*WIDTH, -1*(WIDTH+1),
				-1, WIDTH-1, WIDTH, WIDTH +1, 0
		};
		
		objWriter = new ObjWriter(this);
	}
	
	@Override
	public void run() {		
        System.out.println("Geometry: Finding regions:");
        System.out.printf("Dimensions: %d, %d, %d\n",imp.getWidth(), imp.getHeight(), imp.getStackSize());
        for(int z = 0; z < imp.getStackSize(); ++z) {
	        for(int y = 0; y < imp.getHeight(); ++y) {
	        	for(int x = 0; x < imp.getWidth(); ++x) {
	        		if(!visitedSet.contains(XYZ(x,y,z))) {
	        			try {
							VisitConnectedRegion(x,y,z);
						} catch (Exception e) {
							e.printStackTrace();
							return;
						}
	        		}
	        	}
	        }
        }
        int i = 0;
	}
	
	protected HashSet<Integer> curRegion;
	byte[] distFromPrevDirs;
	//public ArrayList<ArrayList<Vector3D>> shapes = new ArrayList<ArrayList<Vector3D>>();
	public ArrayList<ArrayList<Integer>> shapesIndices = new ArrayList<ArrayList<Integer>>(0);
	private void VisitConnectedRegion(int x, int y, int z) throws Exception {
		//ArrayList<Vector3D> curShape = new ArrayList<Vector3D>();
		ArrayList<Integer> curShapeIndices = new ArrayList<Integer>();
		curRegion.clear(); findRegion(x,y,z);
		System.out.println(curRegion.toString());
		
		int iStart = Collections.min(curRegion); // min(z): top left corner
		int iCur = iStart;
		Vector3D vStart = new Vector3D(x,y,z);
		Vector3D vCur = vStart.clone();
		int maxZ = Collections.max(curRegion) / XY;
		
		byte[] nextDirs = new byte[maxZ-z+1];  // Dir to next (CC) planar filled vertex.
		int[] nextIndices = new int[maxZ-z+1]; // Index rep. of the next vertex 
		distFromPrevDirs = new byte[maxZ-z+1]; // Dist of new dir from the prev.
		byte[] dist = {0}; 					   // 0 := V_ISOLATED returned
		
		// Initialize: dir to next adjacent for each level (XY planes):
		for(int i = 0; i < nextDirs.length; ++i) {
			nextDirs[i] = GetCCIndexPlanar(XYZ(x+1,y,z+i),XYZ(x,y,z+i), dist);
			nextIndices[i] = XYZ(x,y,z+i) + Adjacents[nextDirs[i]];
			distFromPrevDirs[i] = dist[0];
		}
		
		// Core loop:
		// Go to earliest angle & lowest for consensus angle
		do {
			curShapeIndices.add(iCur);
			
			int idx = getNextDirIdx();
			
			while(!isValidPath(iCur, nextIndices[idx])) {
				distFromPrevDirs[idx] += 1;
				idx = getNextDirIdx();
			}
			
			iCur = nextIndices[idx]; // next vertex
			int prevPlanar = iCur - Adjacents[nextDirs[idx]]; // DEBUG: this could be problematic at edges/extents.
			nextDirs[idx] = GetCCIndexPlanar(prevPlanar, iCur, dist);
			nextIndices[idx] = iCur + Adjacents[nextDirs[idx]];
			
			int temp = distFromPrevDirs[idx];
			for(int i = 0; i < distFromPrevDirs.length; ++i) {
				if(distFromPrevDirs[i] == temp) {
					distFromPrevDirs[i] = dist[0];
				}
				else if(i != idx) {distFromPrevDirs[i] -= dist[0];}
			}
		} while(iCur != iStart);
		
		shapesIndices.add(curShapeIndices);
		objWriter.saveFile("test.obj");
	}
	
	boolean isValidPath(int prev, int next) {
		if(((int)Math.abs(next-prev) % (int)(WIDTH*HEIGHT)) == 0){
			int length = WIDTH*HEIGHT;
			while(length < Math.abs(next-prev)) {
				if(!curRegion.contains(Math.max(next, prev) - length)) {
					return false;
				}
				length += WIDTH*HEIGHT;
			}
		}
		return true;
	}
	
	private int getNextDirIdx(){
		byte min = 9; int idx = 0;
		// Favors the z bottommost (first) index
		for(int i = 0; i < distFromPrevDirs.length; ++i) {
			if(distFromPrevDirs[i] < min) {
				min = distFromPrevDirs[i];
				idx = i;
			}
		}
		return idx;
	}
	
	
	// Precondition: indices must be on the same z plane
	// Check for the next counter-clockwise index from the vector cur->prev
	byte GetCCIndexPlanar(int iPrev, int iCur, /*ref*/byte[] dist) throws Exception
	{
		// Next direction must be at least 90 deg. away from the previous.
		byte startdir = (byte)(GetStartDir(iPrev, iCur)+2);
		
		// Use Vector2D when on edges:
		if(visitedSet.onEdge(iCur)) {
			int edgeIdx = visitedSet.getEdgeIndex(iCur);
			for (byte i = startdir; i < startdir+6; ++i) {
				byte imod = (byte)(i % 8);
				if(!outOfBounds[edgeIdx][imod]) {
					if (curRegion.contains(iCur + Adjacents[imod])) {
						dist[0] = (byte)(2 + i - startdir);
						return imod;
					}
				}
			}
			return V_ISOLATED;
		}
		
		// Check start and next 5 dirs for a point on this plane:
		for (byte i = startdir; i < startdir+6; ++i) {
			byte imod = (byte)(i % 8);
			if (curRegion.contains(iCur + Adjacents[imod])) {
				dist[0] = (byte)(2 + i - startdir);
				return imod;
			}
		}
		return V_ISOLATED;
	}
	
	// Precondition: indices must be on the same z plane:
	byte GetStartDir(int iPrev, int iCur) throws Exception {
		int diff = iCur-iPrev;
		if(diff == -1) {return 0;}     		 	  // right (1,0)
		else if(diff == WIDTH-1) {return 1;} 	  // up-right (1,1)
		else if(diff == WIDTH) {return 2;}   	  // up (0,1)
		else if(diff == WIDTH+1) {return 3;}      // up-left (-1,1)
		else if(diff == 1) {return 4;}     		  // left (-1,0)
		else if(diff == -1*(WIDTH-1)) {return 5;} // down-left (-1,-1)
		else if(diff == -WIDTH) {return 6;}       // down (0,-1)
		else if(diff == -1*(WIDTH+1)) {return 7;} // down-right (1,-1)
		else if(diff == 0) {return 8;}
		else throw new Exception("Invalid index for start direction.");
	}
	
	Vector2D[] Adjacents2D = {
			new Vector2D(1,0), new Vector2D(1,1), new Vector2D(0,1),
			new Vector2D(-1,1), new Vector2D(-1,0), new Vector2D(-1,-1),
			new Vector2D(0,-1), new Vector2D(1,-1), new Vector2D(0,0)
	};
	
	private int xyz = 0;
	void findRegion(int targetValue, int x, int y, int z) 
	{ 
		if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) 
	        return;
		xyz = XYZ(x, y, z);
		if(visitedSet.contains(xyz))
	    	return;
		imp.setSlice(z+1);
		int local = imp.getProcessor().get(x, y);
	    if (imp.getProcessor().get(x, y) != targetValue)
	        return;
	    
	    //curRegion.add(new Vector3D(x, y, z)); // Add the vertex.
	    curRegion.add(xyz);
	    visitedSet.put(xyz);
	  
	    // Recur for north, east, south and west 
	    findRegion(targetValue,x+1, y, z);
	    findRegion(targetValue,x-1, y, z);
	    findRegion(targetValue,x, y+1, z);
	    findRegion(targetValue,x, y-1, z);
	    findRegion(targetValue,x, y, z+1);
	    findRegion(targetValue,x, y, z-1);
	} 
	  
	void findRegion(int x, int y, int z) 
	{ 
		imp.setSlice(z+1);
	    int targetValue = imp.getProcessor().get(x, y);
	    findRegion(targetValue, x, y, z); 
	} 
	
	// For an edge index, is a given direction out of bounds?
	// indexing: [edgeIdx][dir]
	private static boolean[][] outOfBounds = {
		{true, true, false, false, false, false, false, true}, // 0,1,7 
		{true, true, true, true, false, false, false, true}, // 0, 1, 2, 3, 7
		{false, true, true, true, false, false, false, false}, // 1,2,3
		{false, true, true, true, true, true, false, false}, // 1,2,3,4,5
		{false, false, false, true, true, true, false, false}, // 3,4,5
		{false, false, false, true, true, true, true, true}, // 3,4,5,6,7
		{false, false, false, false, false, true, true, true}, // 5,6,7
		{true, true, false, false, false, true, true, true}, // 0,1,5,6,7
	};
}
