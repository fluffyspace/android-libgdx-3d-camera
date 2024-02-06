package com.mygdx.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
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
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import java.awt.geom.AffineTransform;
import java.util.Arrays;

public class MyGdxGame extends ApplicationAdapter implements InputProcessor {
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
	double[] camera_coordinates;
	double[] object_coordinates;
	DeviceCameraControl deviceCameraControl;
	int fov = 50;

	private ExtendViewport viewport;

	Vector3 tp = new Vector3();
	boolean dragging;

	public final static float SCALE = 32f;
	public final static float INV_SCALE = 1.f/SCALE;
	// this is our "target" resolution, note that the window can be any size, it is not bound to this one
	public final static float VP_WIDTH = 1280 * INV_SCALE;
	public final static float VP_HEIGHT = 720 * INV_SCALE;

	@Override
	public boolean keyDown(int keycode) {
		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		// ignore if its not left mouse button or first touch pointer
		if (button != Input.Buttons.LEFT || pointer > 0) return false;
		cam.unproject(tp.set(screenX, screenY, 0));
		System.out.println(tp.toString());
		dragging = true;
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if (button != Input.Buttons.LEFT || pointer > 0) return false;
		cam.unproject(tp.set(screenX, screenY, 0));
		dragging = false;
		return true;
	}

	@Override
	public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
		return false;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		if (!dragging) return false;
		cam.unproject(tp.set(screenX, screenY, 0));
		return true;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		return false;
	}

	@Override
	public boolean scrolled(float amountX, float amountY) {
		return false;
	}

	@Override public void resize (int width, int height) {
		// viewport must be updated for it to work properly
		if(viewport != null) {
			viewport.update(width, height, true);
		}
	}

	public enum Mode {
		normal,
		prepare,
		preview,
		takePicture,
		waitForPictureReady,
	}


	private Mode mode = Mode.normal;
	float[] diff;
	MyGdxGame(OnDrawFrame onDrawFrame, double[] camera_coordinates, double[] object_coordinates, DeviceCameraControl deviceCameraControl){
		this.onDrawFrame = onDrawFrame;
		this.camera_coordinates = camera_coordinates;
		this.object_coordinates = object_coordinates;
		this.deviceCameraControl = deviceCameraControl;
		//diff = new float[]{(float) (object_coordinates[0] - camera_coordinates[0]), (float) (object_coordinates[2] - camera_coordinates[2]), (float) (object_coordinates[1] - camera_coordinates[1])};
		diff = new float[]{(float) (camera_coordinates[0] - object_coordinates[0]), (float) (camera_coordinates[2] - object_coordinates[2]), (float) (camera_coordinates[1] - object_coordinates[1])};
		float scalar = 10000;
		diff[0] = diff[0]*scalar;
		diff[1] = diff[1]*scalar;
		diff[2] = diff[2]*scalar;
		System.out.println("Dobio sam " + Arrays.toString(camera_coordinates) + " i " + Arrays.toString(object_coordinates));
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
		cam = new PerspectiveCamera(81, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 0, 0);
		cam.lookAt(1,0,0);
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
		instance.transform.setToTranslation(10, 0, 0);
		//instance.transform.translate(diff[0], diff[1], diff[2]);
		//instance.transform.rotate(new Vector3(0f, 1f, 0f), 90f);
		System.out.println(Arrays.toString(diff));

		modelBatch = new ModelBatch();
		//viewport = new ExtendViewport(VP_WIDTH, VP_HEIGHT, cam);
		Gdx.input.setInputProcessor(this);
	}
	final Vector3 tmp = new Vector3();
	//Quaternion qq = new Quaternion(1, 0, 0, 1);
float taa = 0;
	@Override
	public void render () {
		cam.fieldOfView = fov;
		// Extract the rotation angle from the rotation matrix
		//float rotationAngle = MathUtils.radiansToDegrees * MathUtils.atan2(rotationMatrix.val[Matrix4.M21], rotationMatrix.val[Matrix4.M22]);

// Set the camera rotation
		float[] floats = onDrawFrame.getLastHeadView();

		//cam.combined.set(floats);
		//cam.rotate(new Matrix4(floats));
		//cam.rotate(new Vector3(1f, 0f, 0f), 1);

		//cam.rotate(q2.mul(q.conjugate()));
		//cam.view.setToRotation(Vector3.X, roll)
		//System.out.println(Arrays.toString(floats));

		//cam.rotate(rotationMatrix);

		//cam.view.set(viewMatrix);
		cam.update();

		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		//Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		//instance.transform.mul()
		Quaternion quat = new Quaternion();
		new Matrix4(floats).getRotation(quat);
		quat = new Quaternion(-quat.z, quat.y, quat.x, quat.w);

		//instance.transform.rotate(new Vector3(0, 1, 0), 10f*Gdx.graphics.getDeltaTime());
		instance.transform.set(new Matrix4());
		//instance.transform.rotate(new Vector3(0, 1, 0), taa);
		//instance.transform.setToTranslation(new Vector3(10, 0, 0));
		//instance.transform.mul(new Matrix4().translate(50, 0, 0));
		taa += 1f;
		instance.transform.mul(new Matrix4().rotate(quat).translate(100, 0, 0));
		//instance.transform.setToRotation(new Vector3(0, 0, 1), 0);
		//instance.transform.rotate(quat);

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
