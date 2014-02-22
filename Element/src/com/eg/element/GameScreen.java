package com.eg.element;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;

public class GameScreen implements Screen {

	public static final float SCALE = 40f;
	public static final float DT = 1f/60f;
	public static final int[] BB = {700, 400};
	
	private final Element game;
	private PerspectiveCamera cam;
	public ModelBatch modelBatch;
	
	private Liquid l;
	private World world;
	private Box2DDebugRenderer debugRenderer;
	private Matrix4 debugMatrix;
	private Vector3 touchPos;
	private CameraInputController camController;
	private Environment environment;
	private Array<ModelInstance> instances = new Array<ModelInstance>();
	private MetaballSystem system;
	
	private FPSLogger fpsLogger;
	
	public GameScreen(Element game){		
		this.game = game;
		
		world = new World(new Vector2(0, -9.8f), true);
		debugRenderer = new Box2DDebugRenderer();
		fpsLogger = new FPSLogger();
		
		modelBatch = new ModelBatch();
		environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
		
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0f, 0f, 20f);
        cam.lookAt(0,0,0);
        cam.near = 0.1f;
        cam.far = 300f;
        cam.update();
        
        touchPos = new Vector3();
        Gdx.input.setCursorCatched(false);
        
        debugMatrix = new Matrix4(cam.combined);
		debugMatrix.scale(1f, 1f, 1f);
        
        l = new Liquid(world);
        
        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(camController);
        
        instances.add(new Quad(world, 0f, -400f/SCALE, 1500f/SCALE, 80f/SCALE, 0f));
        instances.add(new Quad(world, -710f/SCALE, -100f/SCALE, 80f/SCALE, 600f/SCALE, 0f));
        instances.add(new Quad(world, 710f/SCALE, 0f, 80f/SCALE, 800f/SCALE, 0f));
        instances.add(new Quad(world, -920f/SCALE, 250f/SCALE, 520f/SCALE, 40f/SCALE, -0.25f));
        
        instances.add(new Circle(world, -400f/SCALE, 0, 50f/SCALE, -0.25f));
        
//        Array<Vector2> pos = new Array<Vector2>();
//        
//        for (int x=-10; x<11; x++) for (int y=-20; y<41; y++){
//        	l.createParticle(1, new Vector2(x/3,y/3));
//        }
        system = new MetaballSystem(l);
	}
	
	@Override
	public void render(float delta) {
		camController.update();
		
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        
        Ray ray = cam.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        ray.getEndPoint(touchPos, -ray.origin.z / ray.direction.z);
        
        //System.out.println(touchPos);
        
        modelBatch.begin(cam);
        l.draw(modelBatch);
        //system.draw(modelBatch);
        modelBatch.render(instances, environment);
        modelBatch.render(l.magnets, environment);
        modelBatch.end();
        
        fpsLogger.log();
        
        //debugRenderer.render(world, debugMatrix);
        
        l.update(delta, touchPos);
        //system.update();
        world.step(DT, 8, 3);

	}

	@Override
	public void resize(int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public void show() {
		// TODO Auto-generated method stub

	}

	@Override
	public void hide() {
		// TODO Auto-generated method stub

	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		debugRenderer.dispose();
		modelBatch.dispose();
        instances.clear();
        Quad.dispose();
        system.dispose();
	}

}
