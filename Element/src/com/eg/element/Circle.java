package com.eg.element;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.World;

public class Circle extends PhysicsModel{
	
	private static Model model;
	
	static{
		ModelBuilder modelBuilder = new ModelBuilder();
        model = modelBuilder.createSphere(1f, 1f, 1f, 64, 64, 
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            Usage.Position | Usage.Normal);
	}

	public Circle(World w, float x, float y, float r, float rotation){
		super(w, model, x, y, 0, 2*r, 2*r, rotation);
		
		CircleShape circleShape = new CircleShape();
        circleShape.setRadius(r);
        body.createFixture(circleShape, 0.0f); 
        circleShape.dispose();
	}
	
	public static void dispose(){
		model.dispose();
	}
}
