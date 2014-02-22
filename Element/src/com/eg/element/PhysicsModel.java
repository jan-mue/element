package com.eg.element;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;

public class PhysicsModel extends ModelInstance{

	protected Body body;
	
	public PhysicsModel(World w, Model model, float x, float y, float z, float scaleX, float scaleY, float rotation){
		super(model);
		
		BodyDef bodyDef = new BodyDef();  
		bodyDef.position.set(new Vector2(x, y));
		bodyDef.angle = rotation;
        body = w.createBody(bodyDef);
        
        transform.translate(x, y, z);
        transform.rotate(0,0,1, (float) Math.toDegrees(rotation));
        transform.scale(scaleX, scaleY, 1f);
	}
	
	public void setTranslation(float x, float y){
		
		float w = getScaleX();
		float h = getScaleY();
		float r = getRotation();
		
		transform.setToTranslation(x, y, -0.5f);
		transform.rotate(0,0,1, (float) Math.toDegrees(r));
		transform.scale(w, h, 1f);
		body.setTransform(x, y, 0);
	}
	
	public Vector2 getTranslation(){
		return body.getPosition().cpy();
	}
	
	public float getScaleX(){
		Vector3 out = new Vector3();
		transform.getScale(out);
		return out.x;
	}
	
	public float getScaleY(){
		Vector3 out = new Vector3();
		transform.getScale(out);
		return out.y;
	}
	
	public float getRotation(){
		return body.getAngle();
	}
}
