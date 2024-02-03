package com.mygdx.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;

import java.awt.geom.AffineTransform;
import java.util.Arrays;

public class MyGdxGame extends ApplicationAdapter {
	static final int WORLD_WIDTH = 100;
	static final int WORLD_HEIGHT = 100;

	private PerspectiveCamera cam;
	private SpriteBatch batch;

	private Sprite mapSprite;
	private Matrix4 rotationMatrix;

	public Model model;
	public ModelInstance instance;

	public ModelBatch modelBatch;

	OnDrawFrame onDrawFrame;
	public Environment environment;
	MyGdxGame(OnDrawFrame onDrawFrame){
		this.onDrawFrame = onDrawFrame;
	}

	@Override
	public void create () {

		mapSprite = new Sprite(new Texture(Gdx.files.internal("badlogic.jpg")));
		mapSprite.setPosition(0, 0);
		mapSprite.setSize(WORLD_WIDTH, WORLD_HEIGHT);

		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();

		rotationMatrix = new Matrix4();

		// Constructs a new OrthographicCamera, using the given viewport width and height
		// Height is multiplied by aspect ratio.
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(10f, 10f, 10f);
		cam.lookAt(0,0,0);
		cam.near = 1f;
		cam.far = 300f;
		cam.update();

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
		environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

		ModelBuilder modelBuilder = new ModelBuilder();
		model = modelBuilder.createBox(5f, 5f, 5f,
				new Material(ColorAttribute.createDiffuse(Color.GREEN)),
				VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
		instance = new ModelInstance(model);

		modelBatch = new ModelBatch();
	}
	Vector3 customUpVector = new Vector3(0, 0, -1); // for example, pointing in the negative Z-axis
	final Vector3 tmp = new Vector3();
	@Override
	public void render () {
		boolean available = Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer);

		if(available) {
			Gdx.input.getRotationMatrix(rotationMatrix.val);
		}
		float azimuth = Gdx.input.getAzimuth();
		float pitch = Gdx.input.getPitch();
		float roll = Gdx.input.getRoll();

		// Extract the rotation angle from the rotation matrix
		//float rotationAngle = MathUtils.radiansToDegrees * MathUtils.atan2(rotationMatrix.val[Matrix4.M21], rotationMatrix.val[Matrix4.M22]);

// Set the camera rotation
		float[] floats = onDrawFrame.getLastHeadView();

		//cam.combined.set(floats);
		//cam.rotate(new Matrix4(floats));
		//cam.rotate(new Vector3(1f, 0f, 0f), 1);

		//cam.rotate(q2.mul(q.conjugate()));
		//cam.view.setToRotation(Vector3.X, roll)
		System.out.println(Arrays.toString(floats));

		//cam.rotate(rotationMatrix);

		//cam.view.set(viewMatrix);
		//cam.update();
		float aspect = cam.viewportWidth / cam.viewportHeight;
		cam.projection.setToProjection(Math.abs(cam.near), Math.abs(cam.far), cam.fieldOfView, aspect);
		cam.view.setToLookAt(cam.position, tmp.set(cam.position).add(cam.direction), cam.up);
		cam.view.mul(new Matrix4(floats));
		//cam.rotate();
		cam.combined.set(cam.projection);
		Matrix4.mul(cam.combined.val, cam.view.val);

		cam.invProjectionView.set(cam.combined);
		Matrix4.inv(cam.invProjectionView.val);
		cam.frustum.update(cam.invProjectionView);

		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);



		modelBatch.begin(cam);
		modelBatch.render(instance, environment);
		modelBatch.end();
	}

	@Override
	public void dispose () {
		batch.dispose();
		mapSprite.getTexture().dispose();
		modelBatch.dispose();
		model.dispose();
	}
}