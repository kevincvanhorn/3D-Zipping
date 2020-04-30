package juicer;

// https://github.com/imagej/tutorials/blob/master/maven-projects/using-ops/src/main/java/UsingOpsLabeling.java

import net.imagej.mesh.*;

import java.awt.List;
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
    public int XY; // WIDTH * HEIGHT
    private float XYInv;
    
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
		public float x, y, z;
		
		public Vector3D(float x, float y, float z) {
			this.x = x;
			this.y = y; 
			this.z = z;
		}
		
		@Override
	    public boolean equals(Object obj) {
	        if (this == obj) return true;
	        if (!(obj instanceof Geometry.Vector3D)) return false;
	        Geometry.Vector3D it = (Geometry.Vector3D) obj;
	        return x == it.x && y == it.y && it.z == z;
	    }
		
		public void plusEq(Vector3D vec) {
			x += vec.x;
			y += vec.y;
			z += vec.z;
		}
		
		 public Vector3D clone() {
	        return new Vector3D(x, y, z);
	    }
		 
		 @Override
		    public String toString() {
		        return "{" + x + ", " + y + ", " + z + "}";
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
		XYInv = 1.0f / (float)(XY);
		
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
        System.out.println("Complete.");
	}
	
	protected HashSet<Integer> curRegion;
	byte[] distFromPrevDirs;
	//public ArrayList<ArrayList<Vector3D>> shapes = new ArrayList<ArrayList<Vector3D>>();
	public ArrayList<ArrayList<Integer>> shapesIndices = new ArrayList<ArrayList<Integer>>(0);
	public ArrayList<ArrayList<Byte>> halfLists = new ArrayList<ArrayList<Byte>>(0);
	private void VisitConnectedRegion(int x, int y, int z) throws Exception {
		//ArrayList<Vector3D> curShape = new ArrayList<Vector3D>();
		ArrayList<Integer> curShapeIndices = new ArrayList<Integer>();
		ArrayList<Byte> curShapeHalfIndices = new ArrayList<Byte>();
		curRegion.clear(); findRegion(x,y,z);
		//System.out.println(curRegion.toString());
		
		int iStart;
		int maxZ = Collections.max(curRegion) / XY;
		int[] iLevelPrev = {0}; 
		int[] iLevelStart = {0}; 
		subRegionVisited.clear();
		
		// Visit sets of 2 z levels of voxels:
		// Modifies curRegion with each visit.
		for(int level = 0; level <= maxZ; level+=2) { // maxZ-z-1 is the highest level
		
			iStart = Collections.min(curRegion); // min(z): top left corner
			while((int)(iStart*XYInv) == level || (int)(iStart*XYInv) == level+1) {
				//curShapeHalfIndices.add((byte)10); // 10 indicates new object
				findNextSubRegion(level+1); // Implicit based on curRegion removals				
				
				iLevelPrev[0] = iStart + Adjacents[0]; // Immediate right - this could be out of bounds
				iLevelStart[0] = iStart;
				visitLevel(level, curShapeIndices, curShapeHalfIndices, iLevelStart, iLevelPrev);
				
				for(int ind : subRegion) {
					if(curRegion.contains(ind)) {
						curRegion.remove(ind);
					}
				}
				
				if(curRegion.size() > 0) {
					iStart = Collections.min(curRegion); // min(z): top left corner
				} else break;
			}

		}
		halfLists.add(curShapeHalfIndices);
		shapesIndices.add(curShapeIndices);
		objWriter.saveFile("test.obj");
	}
	
	
	protected HashSet<Integer> subRegion = new HashSet<Integer>();
	protected HashSet<Integer> subRegionVisited = new HashSet<Integer>();
	void findNextSubRegion(int maxZ) 
	{ 
		subRegion.clear();
		//subRegionVisited.clear();
		int iStart = Collections.min(curRegion); // min(z): top left corner
		
	    //subRegion.add(iStart);
		findSubRegion(iStart, maxZ);
	} 
	
	void findSubRegion(int iVal, int maxZ) 
	{ 
		// Base Case
		if((int)(iVal * XYInv) > maxZ) return;
		else if(subRegionVisited.contains(iVal)) return;
		else if(!curRegion.contains(iVal)) return;
			
		// Add
	    subRegion.add(iVal);
	    subRegionVisited.add(iVal);
	    
	    // Recursion:
	    if(visitedSet.onEdge(iVal)) {
			int edgeIdx = visitedSet.getEdgeIndex(iVal);
			// Check planar 4-connected:
			for(int dir = 0; dir <= 6; dir+=2) {
				if(!outOfBounds[edgeIdx][dir]) {
			    	findSubRegion(iVal + Adjacents[dir], maxZ);
			    }
			}
			
		}
	    else {
	    	for(int dir = 0; dir <= 6; dir+=2) {
	    		findSubRegion(iVal + Adjacents[dir], maxZ);
			}
	    }
		// Check Z:
		findSubRegion(iVal + XY, maxZ);
		findSubRegion(iVal - XY, maxZ);
	} 
	
	// HALF-PRECISION:
	// Represented with an accompanying list: halfList [z up 1/2 :1] 1bit [dir from vertex 0-7] 3bits
	
	void visitLevel(int z, ArrayList<Integer> curShapeIndices, ArrayList<Byte> halfList, int[] iStart, int[] iPrev) throws Exception {
		int start = iStart[0];
		int prev = iPrev[0];
		int cur = start;
		int start2 = start + XY; // level 2 start 
		
		byte dir = 0; // [0]->
		
		// Direction stats, 0: lower, 1: upper
		byte[] nextDirs  = new byte[2]; // Dir to next (CC) planar filled vertex.
		int[] nextIndices = new int[2]; // Index rep. of the next vertex 
		distFromPrevDirs = new byte[2]; // Dist of new dir from the prev.
		byte[] dist = {0}; 			    // 0 := V_ISOLATED returned
		
		// Check (z+) and (x-,z+) for half start vertex:
		if(subRegion.contains(start + WIDTH*HEIGHT)) {
			halfList.add((byte)8); // z.5
		}
		else if(subRegion.contains(start + WIDTH*HEIGHT + Adjacents[0])) {
			halfList.add((byte)0); // z.5,x.5
		}
		else {
			halfList.add((byte)-1); // null
		}
		
		// Initialize:
		for(int i = 0; i < 2; ++i) {
			nextDirs[i] = GetCCIndexPlanar(prev + XY*(i),cur + XY*(i), dist);
			if(nextDirs[i] == 8 && subRegion.contains(cur + Adjacents[0])) {
				nextDirs[i] = 0;
				start2 = cur+Adjacents[0];
			}
			nextIndices[i] = cur+ XY*(i) + Adjacents[nextDirs[i]]; // Same index if no adjacent found.
			distFromPrevDirs[i] = dist[0];
		}
		
		int prevDir = 0;
		boolean usePillar = true;
		boolean prevPillar = true;
		
		int inARowStartDir = start;
		int inARowCnt = 0;
		
		// CORE LOOP:
		do {
			curShapeIndices.add(cur);
			
			// Turn around
			if(nextDirs[0] == 8 && nextDirs[1] == 8) {
				if(subRegion.contains(nextIndices[0] + Adjacents[(byte)Math.floorMod(prevDir+3,8)])) {
					nextDirs[0] = (byte)Math.floorMod(prevDir+3,8);
				}else {nextDirs[0] = (byte)Math.floorMod(prevDir+4,8);}
				if(subRegion.contains(nextIndices[1] + Adjacents[(byte)Math.floorMod(prevDir+3,8)])) {
					nextDirs[1] = (byte)Math.floorMod(prevDir+3,8);
				}else {nextDirs[1] = (byte)Math.floorMod(prevDir+4,8);}
				nextIndices[0] = nextIndices[0] + Adjacents[nextDirs[0]];
				nextIndices[1] = nextIndices[1] + Adjacents[nextDirs[1]];
			}
			
			usePillar = true;
			int idx = getNextDirIdx(false);
			
			int distDiff = Math.abs(distFromPrevDirs[0] - distFromPrevDirs[1]);
			int distSum = distFromPrevDirs[0] - distFromPrevDirs[1];
			if(nextDirs[0] == nextDirs[1]){
				// Go to [0] and add half z+
				cur = nextIndices[0];
				dir = nextDirs[0];
				
				if(nextIndices[0] == nextIndices[1]-XY) {
					halfList.add((byte)8); // Vertical: z + 0.5	
				}
				else if(halfList.size() > 0) {
					halfList.add(halfList.get(halfList.size()-1)); // Add previous element.
				}
				usePillar = prevPillar;
			}
			else if(nextIndices[1] == nextIndices[0]+XY) {
				// Vertical
				halfList.add((byte)8);
				cur = nextIndices[0];
			}
			else if(distDiff == 1 && !subRegion.contains(nextIndices[0]+XY) && subRegion.contains(nextIndices[0]+Adjacents[(byte)Math.floorMod(nextDirs[0]+1,8)])) { //  distFromPrevDirs[0] != 0 && 
				// Inner Diagonals
				cur = nextIndices[0];
				dir = nextDirs[idx];
				idx = 0;
				halfList.add((byte)GetStartDir(nextIndices[1]-XY, nextIndices[0]));
				usePillar = false;
			}
			else if(idx == 1 && subRegion.contains(nextIndices[idx] - XY)) {
				// Triangle contained to lower or pillar:
				cur = nextIndices[0];
				if(subRegion.contains(cur + XY)) { 
					dir = nextDirs[idx]; 
					halfList.add((byte)8); // Pillar
					idx =0;
				}
				else {
					dir = nextDirs[1]; 
					halfList.add((byte)-1); // single point
					idx = 0;
				}
			}
			else if(idx == 0 && subRegion.contains(nextIndices[idx] + XY)) {
				// Triangle contained to upper:
				cur = nextIndices[1];
				nextIndices[0] = cur - XY;
				idx = 1;
				dir = nextDirs[0];
				halfList.add((byte)-1); // single point
			}
			else {
				// Use idx
				cur = nextIndices[idx];
				dir = nextDirs[idx];
				
				if(idx == 0 && subRegion.contains(cur + XY)) {
					halfList.add((byte)8);
				}
				else if(idx == 1 && subRegion.contains(cur - XY)) {
					halfList.add((byte)8);
					cur -= XY;
				}
				else {halfList.add((byte)-1);}
			}
			
			// Use [1] directly if the inner vertex is closer in to the center based on dir
			if(!usePillar || (idx == 1 && !subRegion.contains(cur - XY))) {// || inner(halfList.get(halfList.size()-1), dir)) { // 
				for(int i = 0; i < 2; ++i) {
					nextDirs[i] = GetCCIndexPlanar2((byte)((dir+4)%8), nextIndices[i], dist);
					//nextDirs[i] = GetCCIndexPlanar2(dir, nextIndices[i], dist);
					nextIndices[i] = nextIndices[i] + Adjacents[nextDirs[i]];
					distFromPrevDirs[i] = dist[0];
				}
			}
			else {
				for(int i = 0; i < 2; ++i) {
					//nextDirs[i] = GetCCIndexPlanar(cur + XY*(i) - Adjacents[dir], cur+ XY*(i), dist);
					nextDirs[i] = GetCCIndexPlanar2((byte)((dir+4)%8), cur+ XY*(i), dist);
					nextIndices[i] = cur + XY*(i) + Adjacents[nextDirs[i]];
					distFromPrevDirs[i] = dist[0];
				}
			}
			
			
			if(inARowCnt <=  0 && dir != inARowStartDir) {
				inARowStartDir = dir;
				inARowCnt = 1;
			}
			else if(dir == inARowStartDir || prevDir == inARowStartDir) {
				inARowCnt = 0;
				curShapeIndices.remove(curShapeIndices.size()-1);
				halfList.remove(halfList.size()-1);
			}
			
			prevDir = dir; prevPillar = usePillar;
		} while(cur != start && cur != start2);
	}
	
	// Check half dir from lower vertex to see if upper vertex is radially out from the lower
	boolean inner(byte halfDir, byte dir) {
		// Act on diagonal half dirs only
		if(halfDir != -1 && halfDir != 8) {
			//if(halfDir == (byte)Math.floorMod(dir -1,8) ||  halfDir == (byte)Math.floorMod(dir -2,8) || halfDir == (byte)Math.floorMod(dir -3,8)) {
			if(halfDir == (byte)Math.floorMod(dir -3,8)) {
				return true;
			}
		}
		return false;
	}
	
	boolean collinear(int prev, int cur) throws Exception {
		return GetStartDir(prev, cur) % 2 == 0;
	}
	
	boolean isValidPath(int prev, int next) {
		if(((int)Math.abs(next-prev) % (int)(WIDTH*HEIGHT)) == 0){
			int length = WIDTH*HEIGHT;
			while(length < Math.abs(next-prev)) {
				if(!subRegion.contains(Math.max(next, prev) - length)) {
					return false;
				}
				length += WIDTH*HEIGHT;
			}
		}
		return true;
	}
	
	private int getNextDirIdx(boolean useMin){
		if(!useMin) {
			byte max = 0; int idx = 0;
			// Favors the z-, closest index
			for(int i = 0; i < distFromPrevDirs.length; ++i) {
				if(distFromPrevDirs[i] > max) {
					max = distFromPrevDirs[i];
					idx = i;
				}
			}
			return idx;
		}
		else {
			byte min = 9; int idx = 0;
			// Favors the z-, closest index
			for(int i = 0; i < distFromPrevDirs.length; ++i) {
				if(distFromPrevDirs[i] < min) {
					min = distFromPrevDirs[i];
					idx = i;
				}
			}
			return idx;
		}
	}
	
	// Precondition: indices must be on the same z plane
	// Check for the next counter-clockwise index from the vector cur->prev
	byte GetCCIndexPlanar(int iPrev, int iCur, /*ref*/byte[] dist) throws Exception
		{
			if(iCur > Collections.max(subRegion)) return 8;
		
			// Next direction must be at least 45 deg. away from the previous.
			byte startdir = (byte)(GetStartDir(iPrev, iCur)+1);
			dist[0] = 0;
			
			// Use Vector2D when on edges:
			if(visitedSet.onEdge(iCur)) {
				int edgeIdx = visitedSet.getEdgeIndex(iCur);
				for (byte i = startdir; i <= startdir+6; ++i) {
					byte imod = (byte)(i % 8);
					if(!outOfBounds[edgeIdx][imod]) {
						if (subRegion.contains(iCur + Adjacents[imod])) {
							dist[0] = (byte)(1 + i - startdir);
							return imod;
						}
					}
				}
				return V_ISOLATED;
			}
			
			// Check start and next 5 dirs for a point on this plane:
			for (byte i = startdir; i < startdir+6; ++i) {
				byte imod = (byte)(i % 8);
				if (subRegion.contains(iCur + Adjacents[imod])) {
					dist[0] = (byte)(1 + i - startdir);
					return imod;
				}
			}
			return V_ISOLATED;
		}
	
	// Precondition: indices must be on the same z plane
	// Check for the next counter-clockwise index from the vector cur->prev
	byte GetCCIndexPlanar2(int dir, int iCur, /*ref*/byte[] dist) throws Exception
	{
		if(iCur > XY*DEPTH) return 8;
		if(iCur < 0) return 8;
		
		// Next direction must be at least 45 deg. away from the previous.
		byte startdir = (byte)((dir+1)%8);
		dist[0] = 0;
		
		if(visitedSet.onEdge(iCur)) {
			int edgeIdx = visitedSet.getEdgeIndex(iCur);
			for (byte i = startdir; i <= startdir+6; ++i) {
				byte imod = (byte)(i % 8);
				if(!outOfBounds[edgeIdx][imod]) {
					if (subRegion.contains(iCur + Adjacents[imod])) {
						dist[0] = (byte)(1 + i - startdir);
						return imod;
					}
				}
			}
			return V_ISOLATED;
		}
		
		// Check start and next 6 dirs for a point on this plane:
		for (byte i = startdir; i < startdir+6; ++i) {
			byte imod = (byte)(i % 8);
			if (subRegion.contains(iCur + Adjacents[imod])) {
				dist[0] = (byte)(1 + i - startdir);
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
		else throw new Exception("Invalid index for start direction: " + iPrev + ", "+iCur+"= "+diff);
	}
	
	Vector2D[] Adjacents2D = {
			new Vector2D(1,0), new Vector2D(1,1), new Vector2D(0,1),
			new Vector2D(-1,1), new Vector2D(-1,0), new Vector2D(-1,-1),
			new Vector2D(0,-1), new Vector2D(1,-1), new Vector2D(0,0)
	};

	
	private int xyz = 0;
	void findRegion(int targetValue, int x, int y, int z) 
	{ 
		//if(curRegion.size() > 100) return;
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
