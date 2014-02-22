/*******************************************************************************
 * Copyright (c) 2013, Daniel Murphy
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 	* Redistributions of source code must retain the above copyright notice,
 * 	  this list of conditions and the following disclaimer.
 * 	* Redistributions in binary form must reproduce the above copyright notice,
 * 	  this list of conditions and the following disclaimer in the documentation
 * 	  and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

//Port to libgdx by Jan Müller
package com.eg.element;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.Transform;

/** An axis-aligned bounding box. */
public class AABB {
  /** Bottom left vertex of bounding box. */
  public final Vector2 lowerBound;
  /** Top right vertex of bounding box. */
  public final Vector2 upperBound;

  /**
   * Creates the default object, with vertices at 0,0 and 0,0.
   */
  public AABB() {
    lowerBound = new Vector2();
    upperBound = new Vector2();
  }

  /**
   * Copies from the given object
   * 
   * @param copy the object to copy from
   */
  public AABB(final AABB copy) {
    this(copy.lowerBound, copy.upperBound);
  }

  /**
   * Creates an AABB object using the given bounding vertices.
   * 
   * @param lowerVertex the bottom left vertex of the bounding box
   * @param maxVertex the top right vertex of the bounding box
   */
  public AABB(final Vector2 lowerVertex, final Vector2 upperVertex) {
    this.lowerBound = lowerVertex.cpy(); // clone to be safe
    this.upperBound = upperVertex.cpy();
  }

  /**
   * Sets this object from the given object
   * 
   * @param aabb the object to copy from
   */
  public final void set(final AABB aabb) {
    Vector2 v = aabb.lowerBound;
    lowerBound.x = v.x;
    lowerBound.y = v.y;
    Vector2 v1 = aabb.upperBound;
    upperBound.x = v1.x;
    upperBound.y = v1.y;
  }

  /**
   * Get the center of the AABB
   * 
   * @return
   */
  public final Vector2 getCenter() {
    final Vector2 center = new Vector2(lowerBound);
    center.add(upperBound);
    center.scl(.5f);
    return center;
  }

  public final void getCenterToOut(final Vector2 out) {
    out.x = (lowerBound.x + upperBound.x) * .5f;
    out.y = (lowerBound.y + upperBound.y) * .5f;
  }

  /**
   * Get the extents of the AABB (half-widths).
   * 
   * @return
   */
  public final Vector2 getExtents() {
    final Vector2 center = new Vector2(upperBound);
    center.sub(lowerBound);
    center.scl(.5f);
    return center;
  }

  public final void getExtentsToOut(final Vector2 out) {
    out.x = (upperBound.x - lowerBound.x) * .5f;
    out.y = (upperBound.y - lowerBound.y) * .5f; // thanks FDN1
  }

  public final void getVertices(Vector2[] argRay) {
    argRay[0].set(lowerBound);
    argRay[1].set(lowerBound);
    argRay[1].x += upperBound.x - lowerBound.x;
    argRay[2].set(upperBound);
    argRay[3].set(upperBound);
    argRay[3].x -= upperBound.x - lowerBound.x;
  }

  /**
   * Combine two AABBs into this one.
   * 
   * @param aabb1
   * @param aab
   */
  public final void combine(final AABB aabb1, final AABB aab) {
    lowerBound.x = aabb1.lowerBound.x < aab.lowerBound.x ? aabb1.lowerBound.x : aab.lowerBound.x;
    lowerBound.y = aabb1.lowerBound.y < aab.lowerBound.y ? aabb1.lowerBound.y : aab.lowerBound.y;
    upperBound.x = aabb1.upperBound.x > aab.upperBound.x ? aabb1.upperBound.x : aab.upperBound.x;
    upperBound.y = aabb1.upperBound.y > aab.upperBound.y ? aabb1.upperBound.y : aab.upperBound.y;
  }

  /**
   * Gets the perimeter length
   * 
   * @return
   */
  public final float getPerimeter() {
    return 2.0f * (upperBound.x - lowerBound.x + upperBound.y - lowerBound.y);
  }

  /**
   * Combines another aabb with this one
   * 
   * @param aabb
   */
  public final void combine(final AABB aabb) {
    lowerBound.x = lowerBound.x < aabb.lowerBound.x ? lowerBound.x : aabb.lowerBound.x;
    lowerBound.y = lowerBound.y < aabb.lowerBound.y ? lowerBound.y : aabb.lowerBound.y;
    upperBound.x = upperBound.x > aabb.upperBound.x ? upperBound.x : aabb.upperBound.x;
    upperBound.y = upperBound.y > aabb.upperBound.y ? upperBound.y : aabb.upperBound.y;
  }

  /**
   * Does this aabb contain the provided AABB.
   * 
   * @return
   */
  public final boolean contains(final AABB aabb) {
    /*
     * boolean result = true; result = result && lowerBound.x <= aabb.lowerBound.x; result = result
     * && lowerBound.y <= aabb.lowerBound.y; result = result && aabb.upperBound.x <= upperBound.x;
     * result = result && aabb.upperBound.y <= upperBound.y; return result;
     */
    // djm: faster putting all of them together, as if one is false we leave the logic
    // early
    return lowerBound.x > aabb.lowerBound.x && lowerBound.y > aabb.lowerBound.y
        && aabb.upperBound.x > upperBound.x && aabb.upperBound.y > upperBound.y;
  }

  public static final boolean testOverlap(final AABB a, final AABB b) {
    if (b.lowerBound.x - a.upperBound.x > 0.0f || b.lowerBound.y - a.upperBound.y > 0.0f) {
      return false;
    }

    if (a.lowerBound.x - b.upperBound.x > 0.0f || a.lowerBound.y - b.upperBound.y > 0.0f) {
      return false;
    }

    return true;
  }

  @Override
  public final String toString() {
    final String s = "AABB[" + lowerBound + " . " + upperBound + "]";
    return s;
  }
  
  public static final void ccomputeAABB(final Shape s, final AABB aabb, final Transform t, int childIndex) {
	  if (s instanceof PolygonShape) computePolygonAABB((PolygonShape) s, aabb, t, childIndex);
	  else computeCircleAABB((CircleShape) s, aabb, t, childIndex);
  }
  
  private static final void computePolygonAABB(final PolygonShape shape, final AABB aabb, final Transform xf, int childIndex) {
	    final Vector2 lower = aabb.lowerBound;
	    final Vector2 upper = aabb.upperBound;
	    final Vector2 v1 = new Vector2();
	    shape.getVertex(0, v1);
	    final float a = xf.getRotation();
	    float s = (float) Math.sin(a);
	    float c = (float) Math.cos(a);
	    final Vector2 xfp = xf.getPosition();
	    float vx, vy;
	    lower.x = (c * v1.x - s * v1.y) + xfp.x;
	    lower.y = (s * v1.x + c * v1.y) + xfp.y;
	    upper.x = lower.x;
	    upper.y = lower.y;

	    for (int i = 1; i < shape.getVertexCount(); ++i) {
	      Vector2 v2 = new Vector2();
	      shape.getVertex(i, v2);
	      // Vector2 v = Mul(xf, m_vertices[i]);
	      vx = (c * v2.x - s * v2.y) + xfp.x;
	      vy = (s * v2.x + c * v2.y) + xfp.y;
	      lower.x = lower.x < vx ? lower.x : vx;
	      lower.y = lower.y < vy ? lower.y : vy;
	      upper.x = upper.x > vx ? upper.x : vx;
	      upper.y = upper.y > vy ? upper.y : vy;
	    }

	    lower.x -= shape.getRadius();
	    lower.y -= shape.getRadius();
	    upper.x += shape.getRadius();
	    upper.y += shape.getRadius();
	  }
  
  private static final void computeCircleAABB(final CircleShape circle, final AABB aabb, final Transform transform, int childIndex) {
	  final float a = transform.getRotation();
	    float s = (float) Math.sin(a);
	    float c = (float) Math.cos(a);
	    final Vector2 tp = transform.getPosition();
	    final float px = c * circle.getPosition().x - s * circle.getPosition().y + tp.x;
	    final float py = s * circle.getPosition().x + c * circle.getPosition().y + tp.y;

	    aabb.lowerBound.x = px - circle.getRadius();
	    aabb.lowerBound.y = py - circle.getRadius();
	    aabb.upperBound.x = px + circle.getRadius();
	    aabb.upperBound.y = py + circle.getRadius();
	  }
}
