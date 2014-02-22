package com.eg.element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ShortArray;

public class MetaballSystem implements Disposable{
	
	private final static float THRESHOLD = 0.99f;
	
	private final Liquid liquid;
	
	private Mesh mesh;	
	private FloatArray vertices;
	private ShortArray indices;
	
	private Array<FloatArray> tmp;
	
	private IntMap<IntArray> additionalCells;
	
	private final ExecutorService service;
	private List<Callable<Integer>> cellTasks;
	
	public MetaballSystem(Liquid liquid){
		
		this.liquid = liquid;
		
		mesh = new Mesh(false, Liquid.MAX_PARTICLES*12, Liquid.MAX_PARTICLES*8, 
                new VertexAttribute(Usage.Position, 3, "a_position"));
		
		vertices = new FloatArray(Liquid.MAX_PARTICLES*12);
		indices = new ShortArray(Liquid.MAX_PARTICLES*4);
		for (int i=0; i<Liquid.MAX_PARTICLES*4; i++) indices.add(i);
		tmp = new Array<FloatArray>();
		
		additionalCells = new IntMap<IntArray>();
        
      //Multithreading
      		int threads = Runtime.getRuntime().availableProcessors();
      		
      		System.out.println(threads+" threads available");
      		
      		service = Executors.newFixedThreadPool(threads);
      		cellTasks = new ArrayList<Callable<Integer>>();
	}
	
	public void draw(ModelBatch batch){
		mesh.render(GL10.GL_POINTS);
	}
	
	public void update(){
		
		calcCells();
		
		updateCellTasks(); // performance killer
		
		try{
			//service.invokeAll(cellTasks);
		}catch (Exception e){
			e.printStackTrace();
		}
		
		//updateVertices();
		
		//ShortArray ind = new ShortArray(indices);
		//ind.truncate(vertices.size/3);
		//mesh.setIndices(ind.toArray());
		//mesh.setVertices(vertices.toArray());
	}
	
	private void calcCells() {
		for (int x : liquid.grid.keys().toArray().items){
			for (int y : liquid.grid.get(x).keys().toArray().items){
				for (int x1=x-2; x1<x+3; x1++){
					for (int y1=y-2; y1<y+3; y1++){
						if(x1==x && y1==y) continue;
						
						if (!liquid.grid.containsKey(x1)){
							if (!additionalCells.containsKey(x1))
								additionalCells.put(x1, new IntArray(100));
							additionalCells.get(x1).add(y1);
							continue;
						}
						if (!liquid.grid.get(x1).containsKey(y1)){
							if (!additionalCells.containsKey(x1))
								additionalCells.put(x1, new IntArray(100));
							additionalCells.get(x1).add(y1);;
						}
					}
				}
			}
		}
	}

	private void updateVertices() {
		vertices.clear();
		for (FloatArray v : tmp){
			if (v!=null)for (int i=0; i<v.size-1; i+=2){
				vertices.add(v.get(i));
				vertices.add(v.get(i+1));
				vertices.add(0);
			}
		}
		tmp.clear();
	}
	
	private void evaluateCell(final int x, final int y){
		final float x1 = x*Liquid.CELL_SIZE;
		final float y1 = y*Liquid.CELL_SIZE;
		
		final float[] corners = new float[8];
		corners[0] = x1; 						corners[1] = y1;		
		corners[2] = x1 + Liquid.CELL_SIZE; 	corners[3] = y1;		
		corners[4] = x1 + Liquid.CELL_SIZE; 	corners[5] = y1 + Liquid.CELL_SIZE;		
		corners[6] = x1; 						corners[7] = y1 + Liquid.CELL_SIZE;
		
		boolean[] result = new boolean[8];
		calcValues(corners, result);
		
		FloatArray verts = new FloatArray(8);
		for (int i=0; i<4; i++){
			
			int c1=(i==3)? 0 : i+1;
			if (result[i]!=result[c1]){
				verts.add((corners[i*2]+corners[(c1)*2])/2);
				verts.add((corners[i*2+1]+corners[(c1)*2+1])/2);
			}
			
			int c2=(i==0)? 3 : i-1;
			if (result[i]!=result[c2]){
				verts.add((corners[i*2]+corners[(c2)*2])/2);
				verts.add((corners[i*2+1]+corners[(c2)*2+1])/2);
			}
		}
		
		tmp.add(verts);
	}
	
	private static boolean containsVector(FloatArray list3d, float x, float y){
		for (int i=0; i<list3d.size-2; i+=3)
			if (list3d.get(i)==x && list3d.get(i+1)==y) return true;		
		return false;
	}
	
//	private static boolean containsVector(Array<Vector2> list, Vector2 vec){
//		for (Vector2 v : list)
//			if (v.x==vec.x && v.y==vec.y) return true;
//		return false;
//	}
	
	private void calcValues(float[] pos, boolean[] out){
		int n=0;
		float r;
		float f;
		
		main:for (int i=0; i<pos.length; i+=2){
			f = 0;			
			for (int index : liquid.activeParticles.toArray()){
				r = 1-liquid.getPos(index).dst(pos[i], pos[i+1]);
				if (r<=0) continue;
				f += r*r*r*(r*(r*6 - 15) + 10);
				if (f>THRESHOLD){
					out[n]=true;
					n++;
					continue main;
				}
			}
			out[n]=false;
			n++;
		}
	}

	@Override
	public void dispose() {
		mesh.dispose();
		
	}
	
	private void updateCellTasks(){
		cellTasks.clear();
		
		for (int x : liquid.grid.keys().toArray().toArray()){
			for (int y : liquid.grid.get(x).keys().toArray().toArray()){
				final int x1 = x;
				final int y1 = y;
				
				Callable<Integer> callable = new Callable<Integer>() {
			        public Integer call(){
			        	evaluateCell(x1,y1);
			        	return x1;
			        }
			    };
			    cellTasks.add(callable);
			}
		}
		
		for (int x : additionalCells.keys().toArray().toArray()){
			for (int y : additionalCells.get(x).toArray()){
				final int x1 = x;
				final int y1 = y;
				
				Callable<Integer> callable = new Callable<Integer>() {
			        public Integer call(){
			        	evaluateCell(x1,y1);
			        	return x1;
			        }
			    };
			    cellTasks.add(callable);
			}
		}
	}

}
