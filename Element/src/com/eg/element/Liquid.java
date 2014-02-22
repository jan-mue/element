package com.eg.element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.QueryCallback;

public class Liquid {
	
	private class Particle
	{
		private Vector2 position;
	    private Vector2 velocity;
	    private boolean alive;
	    private int index;
	    
	    private FloatArray distances;
	    private IntArray neighbors;
	    private int neighborCount;
	    
	    private int ci;		//keep track of the grid coordinates
	    private int cj;
	    
	    private float p;
	    private float pnear;
	    
	    private static final int MAX_FIXTURES_TO_TEST = 20;
	    private final Array<Fixture> fixturesToTest;
	    private int numFixturesToTest;
	    private final Array<Vector2> collisionVertices;
	    private final Array<Vector2> collisionNormals;

	    private Particle(Vector2 position, Vector2 velocity, boolean alive) {
	        this.position = position;
	        this.velocity = velocity;
	        this.alive = alive;
	        
	        distances = new FloatArray(MAX_NEIGHBORS);
	        neighbors = new IntArray(MAX_NEIGHBORS);
	        
	        fixturesToTest = new Array<Fixture>(MAX_FIXTURES_TO_TEST);
	        collisionVertices = new Array<Vector2>(8);
	        collisionNormals = new Array<Vector2>(8);
	        
	        for (int i=0; i<MAX_FIXTURES_TO_TEST; i++) fixturesToTest.add(null);
	        for (int i=0; i<8; i++){
	        	collisionVertices.add(new Vector2());
	        	collisionNormals.add(new Vector2());
	        }
	        
	    }
	}
	
	public static final int MAX_PARTICLES = 3000;
	public static final float RADIUS = 0.9f;
	private static final float VISCOSITY = 0.004f;
	
	private static final float CHARGE = 6E-20f;
	
	private static final float IDEAL_RADIUS = 50f;
	private static final float MULTIPLIER = IDEAL_RADIUS / RADIUS;
	private static final float IDEAL_RADIUS_SQ = IDEAL_RADIUS * IDEAL_RADIUS;
	
	public static final float CELL_SIZE = 0.6f;
	
	private static final int MAX_NEIGHBORS = 75;
	
	private static final Object LOCK = new Object();
	
	private int activeParticleCount;
	private Array<Particle> liquid;
	public IntArray activeParticles;
	
	private Array<Vector2> delta;
	private final Array<Vector2> scaledPositions;
	private final Array<Vector2> scaledVelocities;
	private Array<IntMap<Vector2>> accumulatedDeltas;
	
	private final Vector2 gravity = new Vector2(0, -9.81f/3000f);
	private final AABB simulationAABB;
	private final World world;
	
	private Vector2 jitter;
	
	public final Array<Magnet> magnets;
	
	//Multithreading
    private final ExecutorService service;
    private final List<Callable<Integer>> prepareTasks, pressureTasks, forcesTasks, collisionTasks;
	
	//Spatial Partitioning grid for dynamic meshing
	public IntMap<IntMap<IntArray>> grid;
	private Model model;
	private ModelInstance instance;
	
	public Liquid(World world){
		//Multithreading
		int threads = Runtime.getRuntime().availableProcessors();
		
		System.out.println(threads+" threads available");
		
		service = Executors.newFixedThreadPool(threads);
		prepareTasks = new ArrayList<Callable<Integer>>(MAX_PARTICLES);
		pressureTasks = new ArrayList<Callable<Integer>>(MAX_PARTICLES);
		forcesTasks = new ArrayList<Callable<Integer>>(MAX_PARTICLES);
		collisionTasks = new ArrayList<Callable<Integer>>(MAX_PARTICLES);
		
		setupThreadedLoops();
		
		//Particle System		
		activeParticles = new IntArray(MAX_PARTICLES);
		liquid = new Array<Particle>(MAX_PARTICLES);
		delta = new Array<Vector2>(MAX_PARTICLES);
		scaledPositions = new Array<Vector2>(MAX_PARTICLES);
		scaledVelocities = new Array<Vector2>(MAX_PARTICLES);
		
		grid = new IntMap<IntMap<IntArray>>();
		accumulatedDeltas = new Array<IntMap<Vector2>>(MAX_PARTICLES);
		
		for (int i = 0; i<MAX_PARTICLES; i++){
		    liquid.add(new Particle(new Vector2(), new Vector2(), false));
		    liquid.get(i).index = i;
		    
		    scaledPositions.add(new Vector2());
	        scaledVelocities.add(new Vector2());
	        delta.add(new Vector2());
		    
		    accumulatedDeltas.add(new IntMap<Vector2>(MAX_NEIGHBORS+1));
		}
		
		ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createSphere(0.1f, 0.1f, 0.1f, 4, 4,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                Usage.Position | Usage.Normal);
        instance = new ModelInstance(model);
		
		jitter = new Vector2();
		
		Vector2 lower= new Vector2(-GameScreen.BB[0], -GameScreen.BB[1]);
		Vector2 upper= new Vector2(GameScreen.BB[0], GameScreen.BB[1]);
		
		simulationAABB = new AABB(lower, upper);
		this.world = world;
		
		magnets = new Array<Magnet>();
		magnets.add(new Magnet(world, new Vector2()));
	}
	
	private int getGridX(float x) { return (int)Math.floor(x / CELL_SIZE); }
	private int getGridY(float y) { return (int)Math.floor(y / CELL_SIZE); }
	
	private void prepareSimulation(int index){
		Particle particle = liquid.get(index);
		
		// Find neighbors
        findNeighbors(particle);
        
        // Scale positions and velocities
        scaledPositions.get(index).set(particle.position.cpy().scl(MULTIPLIER));
        scaledVelocities.get(index).set(particle.velocity.cpy().scl(MULTIPLIER));

        // Reset deltas
        delta.get(index).set(0f, 0f);
        accumulatedDeltas.get(index).clear();
        
        // Reset pressures
        liquid.get(index).p = 0f;
        liquid.get(index).pnear = 0f;
        
        // Reset collision information
        particle.numFixturesToTest = 0;
	}
	
	// prepareCollisions
	private void prepareCollisions()
	{
	    // Query the world using the screen's AABB
	    world.QueryAABB(new QueryCallback(){
	    	public boolean reportFixture(Fixture fixture){
	    		IntMap<IntArray> collisionGridX;
	    	    IntArray collisionGridY;
	    		
	    		AABB aabb = new AABB();
	            AABB.ccomputeAABB(fixture.getShape(), aabb, fixture.getBody().getTransform(), 0);
	            
//	            System.out.println(fixture.getShape());
//	            System.out.println(aabb);

	            // Get the bottom left corner of the AABB in grid coordinates
	            int Ax = getGridX(aabb.lowerBound.x);
	            int Ay = getGridY(aabb.lowerBound.y);

	            // Get the top right corner of the AABB in grid coordinates
	            int Bx = getGridX(aabb.upperBound.x) + 1;
	            int By = getGridY(aabb.upperBound.y) + 1;

	            // Loop through all the grid cells in the fixture's AABB
	            for (int i = Ax; i < Bx; i++){
	                for (int j=Ay; j < By; j++){
	                    if (grid.containsKey(i) && (collisionGridX = grid.get(i)).containsKey(j)){
	                    	collisionGridY = collisionGridX.get(j);
	                        // Tell any particles we find that this fixture should be tested
	                        for (int k=0; k < collisionGridY.size; k++){
	                            Particle particle = liquid.get(collisionGridY.get(k));
	                            if (particle.numFixturesToTest < Particle.MAX_FIXTURES_TO_TEST){
	                                particle.fixturesToTest.set(particle.numFixturesToTest, fixture);
	                                particle.numFixturesToTest++;
	                            }
	                        }
	                    }
	                }
	            }
	    		return true;
	    	}
	    }, simulationAABB.lowerBound.x, simulationAABB.lowerBound.y, simulationAABB.upperBound.x, simulationAABB.upperBound.y);
	}
	
	private void calculatePressure(int index){
		Particle particle = liquid.get(index);
		
        for (int a=0; a<particle.neighborCount; a++) {
        	//System.out.println("a "+a+" index "+index);
            float distanceSq = scaledPositions.get(particle.neighbors.get(a)).dst2(scaledPositions.get(index));

            //within idealRad check
            if (distanceSq < IDEAL_RADIUS_SQ) {
                particle.distances.set(a, (float)Math.sqrt(distanceSq));
                //if (particle.distances[a] < Settings.EPSILON) particle.distances[a] = IDEAL_RADIUS - .01f;
                float oneminusq = 1.0f - (particle.distances.get(a) / IDEAL_RADIUS);
                particle.p = (particle.p + oneminusq*oneminusq);
                particle.pnear = (particle.pnear + oneminusq*oneminusq*oneminusq);
            } else {
                particle.distances.set(a, Float.MAX_VALUE);
            }
        }
	}
	
	private void calculateForces(int index){
		Particle particle = liquid.get(index);
		
		float pressure = (particle.p - 5f) / 2.0f; //normal pressure term
        float presnear = particle.pnear / 2.0f; //near particles term
        Vector2 change = new Vector2(0f, 0f);
        for (int a = 0; a < particle.neighborCount; a++)
        {
        	Vector2 relativePosition = scaledPositions.get(particle.neighbors.get(a)).cpy().sub(scaledPositions.get(index));

            if (particle.distances.get(a) < IDEAL_RADIUS)
            {
                float q = particle.distances.get(a) / IDEAL_RADIUS;
                float oneminusq = 1.0f - q;
                float factor = oneminusq * (pressure + presnear * oneminusq) / (2.0F * particle.distances.get(a));
                Vector2 d = relativePosition.cpy().scl(factor);
                Vector2 relativeVelocity = scaledVelocities.get(particle.neighbors.get(a)).cpy().sub(scaledVelocities.get(index));

                factor = VISCOSITY * oneminusq * GameScreen.DT;
                d.sub(relativeVelocity.cpy().scl(factor));
                accumulatedDeltas.get(index).put(particle.neighbors.get(a), d.cpy());
                change.sub(d);
            }
        }
        accumulatedDeltas.get(index).put(index, change.cpy());
        particle.velocity.add(gravity);
        
        //Calculate electrostatic forces
        for (Magnet magnet : magnets){
        Vector2 distance = magnet.getTranslation().sub(particle.position);
        if (distance.len()<10 && distance.len()>1){
        	float coulombForce = (float) (CHARGE*magnet.charge/(distance.len2()*4*Math.PI));
        	Vector2 force = distance.nor().scl(coulombForce);
        	particle.velocity.add(force);
        }
        }
	}
	
	private final static Vector2 cross(Vector2 a, float s) {
	    return new Vector2(s * a.y, -s * a.x);
	}
	
	private final static void computeNormals(PolygonShape shape, final Array<Vector2> out) throws IllegalArgumentException{
		final Vector2 edge = new Vector2();
		final Vector2 vec = new Vector2();

	    // Compute normals. Ensure the edges have non-zero length.
	    for (int i=0; i < shape.getVertexCount(); ++i) {
	      final int i1 = i;
	      final int i2 = i + 1 < shape.getVertexCount() ? i + 1 : 0;
	      synchronized(LOCK){
	      shape.getVertex(i2, edge);
	      shape.getVertex(i1, vec);
	      }
	      edge.sub(vec);

	      if(edge.len()==0 || cross(edge, 1f).len()==0) throw new IllegalArgumentException();
	      out.get(i).set(cross(edge, 1f));
	      out.get(i).nor();
	    }
	}
	
	private void resolveCollision(int index){
	    final Particle particle = liquid.get(index);
	    
//	    if (particle.numFixturesToTest>0)
//	    System.out.println("Fixtures for particle "+index+": "+particle.numFixturesToTest);

	    // Test all fixtures stored in this particle
	    for (int i=0; i < particle.numFixturesToTest; i++){
	        Fixture fixture = particle.fixturesToTest.get(i);

	        // Determine where the particle will be after being moved
	        Vector2 newPosition = particle.position.cpy().add(particle.velocity.cpy().add(delta.get(index)));

	        // Test to see if the new particle position is inside the fixture
	        if (fixture.testPoint(newPosition))
	        {
	            Body body = fixture.getBody();
	            Vector2 closestPoint = new Vector2(0f, 0f);
	            Vector2 normal = new Vector2(0f, 0f);

	            // Resolve collisions differently based on what type of shape they are
	            if (fixture.getType().equals(Shape.Type.Polygon))
	            {
	                PolygonShape shape = (PolygonShape) fixture.getShape();
	                Transform collisionXF = body.getTransform();
	                try{
	                	computeNormals(shape, particle.collisionNormals);
	                }catch(Exception e){
	                	e.printStackTrace();
	                }
	                
	                for (int v = 0; v < shape.getVertexCount(); v++)
	                {
	                    // Transform the shape's vertices from local space to world space
	                	Vector2 vec = new Vector2();
	                	synchronized(LOCK){		shape.getVertex(v, vec);}
	                	particle.collisionVertices.get(v).set(collisionXF.mul(vec));
	                	
//	                	Vector2 offset=body.getLocalCenter().cpy().sub(particle.collisionVertices.get(v)).nor();
//	                	offset.scl((float) Math.sqrt(0.005));
//	                	particle.collisionVertices.get(v).add(offset);

	                    // Transform the shape's normals rotation
	                    particle.collisionNormals.get(v).rotate((float) Math.toDegrees(collisionXF.getRotation()));
	                }

	                // Find closest edge
	                float shortestDistance = 9999999f;
	                for (int v = 0; v < shape.getVertexCount(); v++)
	                {
	                	//System.out.println("Shape "+i+" Vertex "+v+": "+particle.collisionVertices.get(v)+
	                	//" Normal: "+particle.collisionNormals.get(v));
	                	
	                    // Project the vertex position relative to the particle position onto the edge's normal to find the distance
	                    float distance = particle.collisionNormals.get(v)
	                    		.dot(particle.collisionVertices.get(v).cpy().sub(particle.position));
	                    if (distance < shortestDistance)
	                    {
	                        // Store the shortest distance
	                        shortestDistance = distance;

	                        // Push the particle out of the shape in the direction of the closest edge's normal
	                        closestPoint.set(particle.collisionNormals.get(v).cpy().scl(distance).add(particle.position));
	                        normal.set(particle.collisionNormals.get(v));
	                    }
	                }
	                particle.position.set(closestPoint.cpy().add(normal.cpy().scl(0.05f)));
	            }
	            else if (fixture.getType().equals(Shape.Type.Circle))
	            {
	                // Push the particle out of the circle by normalizing the circle's center relative to the particle position,
	                // and pushing the particle out in the direction of the normal
	                CircleShape shape = (CircleShape) fixture.getShape();
	                Vector2 center = shape.getPosition().cpy().add(body.getPosition());
	                Vector2 difference = particle.position.cpy().sub(center);
	                normal.set(difference);
	                normal.nor();
	                closestPoint.set(center.cpy().add(difference.scl(shape.getRadius() / difference.len())));
	                //System.out.println("Old: "+particle.position+" New: "+closestPoint.cpy().add(normal.cpy().scl(0.05f)));
	                particle.position.set(closestPoint.cpy().add(normal.cpy().scl(0.05f)));
	            }

	            // Update velocity
	            particle.velocity.sub(normal.cpy().scl(particle.velocity.dot(normal)*1.2f));

	            // Reset delta
	            delta.get(index).set(0f, 0f);
	        }
	    }
	}
	
	private void moveParticle(int index){
		Particle particle = liquid.get(index);
		int x = getGridX(particle.position.x);
        int y = getGridY(particle.position.y);
        
        //Update velocity
        particle.velocity.add(delta.get(index));
        // Update Position
        particle.position.add(delta.get(index));
        particle.position.add(particle.velocity);
        
        // Update particle cell
        if (particle.ci == x && particle.cj == y)
            return;
        else
        {
        	grid.get(particle.ci).get(particle.cj).removeValue(index);

            if (grid.get(particle.ci).get(particle.cj).size == 0){
            	grid.get(particle.ci).remove(particle.cj);

                if (grid.get(particle.ci).size == 0){
                    grid.remove(particle.ci);
                }
            }

            if (!grid.containsKey(x))
                grid.put(x, new IntMap<IntArray>());
            if (!grid.get(x).containsKey(y))
                grid.get(x).put(y, new IntArray(20));

            grid.get(x).get(y).add(index);
            particle.ci = x;
            particle.cj = y;
        }
	}
	
	private void findNeighbors(Particle particle){
	    particle.neighborCount = 0;
	    
	    IntMap<IntArray> gridX;
	    IntArray gridY;

	    for (int nx=-1; nx<2; nx++){
	        for (int ny=-1; ny<2; ny++){
	            int x = particle.ci + nx;
	            int y = particle.cj + ny;
	            if (grid.containsKey(x) && (gridX = grid.get(x)).containsKey(y)){
	            	gridY = gridX.get(y);
	                for (int a=0; a<gridY.size; a++){
	                    if (gridY.get(a) != particle.index){
	                    	if (particle.neighbors.size<particle.neighborCount+1){                    	
		                    	particle.neighbors.add(gridY.get(a));
		                        particle.distances.add(0f);
	                    	}else 
	                    		particle.neighbors.set(particle.neighborCount, gridY.get(a));
	                        particle.neighborCount++;

	                        if (particle.neighborCount >= MAX_NEIGHBORS)
	                            return;
	                    }
	                }
	            }
	        }
	    }
	}
	
	public Vector2 getPos(int index){ return liquid.get(index).position.cpy(); }
	
	public void createParticle(int numParticlesToSpawn, Vector2 mouse)
	{
	    Array<Particle> inactiveParticles = new Array<Particle>(MAX_PARTICLES-activeParticleCount);
	    for (Particle p : liquid) if (!p.alive) inactiveParticles.add(p);
	    if (inactiveParticles.size>=numParticlesToSpawn)
	    	inactiveParticles.truncate(numParticlesToSpawn);

	    for (Particle particle : inactiveParticles)
	    {
	            jitter.set((float)(Math.random() * 2 - 1), (float)(Math.random()) - 0.5f);

	            particle.position.set(mouse.cpy().add(jitter));
	            particle.velocity.set(0f, 0f);
	            particle.alive = true;
	            particle.ci = getGridX(particle.position.x);
	            particle.cj = getGridY(particle.position.y);

	            // Create grid cell if necessary
	            if (!grid.containsKey(particle.ci))
	            	grid.put(particle.ci, new IntMap<IntArray>());
	            if (!grid.get(particle.ci).containsKey(particle.cj))
	            	grid.get(particle.ci).put(particle.cj, new IntArray());
	            
	            grid.get(particle.ci).get(particle.cj).add(particle.index);

	            activeParticles.add(particle.index);
	            activeParticleCount++; 
	        
	    }
	    
	    //System.out.println("Active particles: "+activeParticleCount);
	}
	
	public void draw(ModelBatch batch)
	{
	    for (int i=0; i<activeParticleCount; i++)
	    {
	    	//if (i>0) break;
	        Particle particle = liquid.get(activeParticles.get(i));
	        //if (particle.index==0) System.out.println("Particle "+particle.index+": x = "
	        //+particle.position.x+" y = "+particle.position.y);
	        instance.transform.setToTranslation(particle.position.x, particle.position.y, 0f);
	        batch.render(instance);
	    }
	}
	
	public void update(float tpf, Vector3 touchPos){
		magnets.get(0).setTranslation(touchPos.x, touchPos.y);
		if (Gdx.input.isTouched() && Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
			boolean intersect=false;
			if (magnets.size>1) for (int i=1; i<magnets.size; i++){
				if (magnets.get(0).overlaps(magnets.get(i))) intersect=true;
			}
			
			if (!intersect && AABB.testOverlap(simulationAABB, magnets.get(0).getAABB()))
				magnets.add(new Magnet(world, magnets.get(0).getTranslation()));
			//System.out.println("Input: x = "+pos.x+" y = "+pos.y);
		}
		
		createParticle(1, new Vector2(-850, 400).div(GameScreen.SCALE));
		
		try{
			processParticles();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	//Multithreading
	private void processParticles()
	        throws InterruptedException, ExecutionException {
	    
	    service.invokeAll(prepareTasks.subList(0, activeParticleCount));
	    
	    prepareCollisions();
	    
	    service.invokeAll(pressureTasks.subList(0, activeParticleCount));
	    
	    service.invokeAll(forcesTasks.subList(0, activeParticleCount));
	    
	    service.invokeAll(collisionTasks.subList(0, activeParticleCount));
	    
	    for (int i=0; i<activeParticleCount; i++) {
	    	moveParticle(activeParticles.get(i));
	    }
	}
	
	private void setupThreadedLoops(){
		for (int i=0; i<MAX_PARTICLES; i++) {
	    	final int index=i;
	    	Callable<Integer> callable = new Callable<Integer>() {
	        	public Integer call(){
	        		int num=activeParticles.get(index);
	        		prepareSimulation(num);
	        		return num;
	        	}
	        };
	        prepareTasks.add(callable);
	    }
		
		for (int i=0; i<MAX_PARTICLES; i++) {
			final int index=i;
	    	Callable<Integer> callable = new Callable<Integer>() {
	        	public Integer call(){
	        		int num=activeParticles.get(index);
	        		calculatePressure(num);
	        		return num;
	        	}
	        };
	        pressureTasks.add(callable);
	    }
		
		for (int i=0; i<MAX_PARTICLES; i++) {
			final int index=i;
	    	Callable<Integer> callable = new Callable<Integer>() {
	        	public Integer call(){
	        		int num=activeParticles.get(index);
	        		calculateForces(num);
	        		
	        		synchronized(LOCK) {
	        			for (int j : accumulatedDeltas.get(num).keys().toArray().items)
	                    {
	                        delta.get(j).add(accumulatedDeltas.get(num).get(j).scl(1/MULTIPLIER));
	                    }
	        		}
	        		return num;
	        	}
	        };
	        forcesTasks.add(callable);
	    }
		
		for (int i=0; i<MAX_PARTICLES; i++) {
			final int index=i;
	    	Callable<Integer> callable = new Callable<Integer>() {
	        	public Integer call(){
	        		int num=activeParticles.get(index);
	        		resolveCollision(num);
	        		return num;
	        	}
	        };
	        collisionTasks.add(callable);
	    }
	}

}
