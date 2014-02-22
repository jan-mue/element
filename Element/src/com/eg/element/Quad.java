package com.eg.element;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;

public class Quad extends PhysicsModel{
	
	private static Model model;
	
	static{
		ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createBox(1f, 1f, 1f, 
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            Usage.Position | Usage.Normal);
	}

	public Quad(World w, float x, float y, float width, float height, float rotation){
		super(w, model, x, y, -0.5f, width, height, rotation);
		
		PolygonShape boxShape = new PolygonShape();  
        boxShape.setAsBox(width/2, height/2);
        body.createFixture(boxShape, 0.0f); 
        boxShape.dispose();
	}
	
	public static void dispose(){
		model.dispose();
	}
}
