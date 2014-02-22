package com.eg.element;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;

public class Magnet extends Quad{
	
	public float charge;
	
	public Magnet(World w, Vector2 translation){
		super(w, translation.x, translation.y, 1.5f, 1.5f, 0);
		
		charge = 10E18f;
	}
	
	public AABB getAABB(){
		float l=getScaleY()/2;
		return new AABB(getTranslation().sub(l,l), getTranslation().add(l,l));
	}
	
	public boolean overlaps(Magnet m){
		return AABB.testOverlap(getAABB(), m.getAABB());
	}

}
