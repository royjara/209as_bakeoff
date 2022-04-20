import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.io.FileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import processing.core.PApplet;
import processing.sound.AudioIn;
import processing.sound.FFT;
import processing.sound.Sound;
import processing.sound.Waveform;
import processing.sound.Amplitude;
import controlP5.*;

/* A class with the main function and Processing visualizations to run the demo */

public class ClassifyVibration extends PApplet {
	
	// GLOBALS ------------------------------------------------------
	String modelname_root = "demo";
	int numberOfModels = 0;
	int testNumber = 0; // can then become current model state variable
	
	boolean openMenu = false;
	
	// noise gate
	Amplitude amp;
	float thresh = 0.1f;
	float release = 20; // max gap size (# of samples) 
	float releaseCounter = 0;
	float currAmp;

	boolean capture = false;
	// END GLOBALS --------------------------------------------------

	FFT fft;
	AudioIn in;
	Waveform waveform;
	int bands = 512;
	int nsamples = 1024;
	float[] spectrum = new float[bands];
	float[] fftFeatures = new float[bands];
	String[] classNames = {"quiet", "hand drill", "whistling", "class clapping"}; // ---------- EDIT WITH OUR CLASSES
	int classIndex = 0;
	int dataCount = 0;

	// New Stuff --------------------------------------------------

//	Map<String, MLClassifier> models = new HashMap<>();
	List<String> models = new ArrayList<String>();

	ControlP5 cp5;
	
	String selectedModel = null;
	int col = color(255);

	String mode = "Train";
	
	// --------------------------------------------------

	MLClassifier classifier;
	
	Map<String, List<DataInstance>> trainingData = new HashMap<>();
	{for (String className : classNames){
		trainingData.put(className, new ArrayList<DataInstance>());
	}}
	
	DataInstance captureInstance (String label){
		DataInstance res = new DataInstance();
		res.label = label;
		res.measurements = fftFeatures.clone();
		return res;
	}
	
	public static void main(String[] args) {
		
		// populate the number of indexes of past models here -----
		// numberOfModels = ...
		
		PApplet.main("ClassifyVibration");
	}
	
	public void settings() {
		size(1200, 750);
	}

	public void setup() {
		
		/* list all audio devices */
		Sound.list();
		Sound s = new Sound(this);
		  
		/* select microphone device */
		s.inputDevice(1);
		    
		/* create an Input stream which is routed into the FFT analyzer */
		fft = new FFT(this, bands);
		in = new AudioIn(this, 0);
		waveform = new Waveform(this, nsamples);
		waveform.input(in);
		amp = new Amplitude(this);
		
		/* start the Audio Input */
		in.start();
		  
		/* patch the AudioIn */
		amp.input(in);
		fft.input(in);

		
		
		cp5 = new ControlP5(this);
		
		// create a toggle and change the default look to a (on/off) switch look
		cp5.addToggle("Train_Test")
		.setPosition(20,20)
		.setSize(50,20)
		.setValue(true)
		.setMode(ControlP5.SWITCH)
		;
		
		
		String cwd = System.getProperty("user.dir");
		File dir = new File(cwd);
		FileFilter fileFilter = new WildcardFileFilter("*.model");
		File[] files = dir.listFiles(fileFilter);
		
		// list all available models
		for (int i = 0; i < files.length; i++) {
		   models.add(files[i].getName());
		}
		cp5.addScrollableList("models")
	     .setPosition(800, 0)
	     .setSize(200, 100)
	     .setBarHeight(20)
	     .setItemHeight(20)
	     .addItems(models)
	     ;
		
	}
	
	public void Train_Test(boolean theFlag) {
		if(theFlag==true) {
			col = color(255);
			mode = "Train";
			println(mode);
		} else {
			col = color(100);
			mode = "Test";
			println(mode);
		}
	}
	
	public void draw() {
		background(39,46,41);
		noFill();
		
		waveform.analyze();
		
		textSize(20);
		currAmp = amp.analyze();
		text("Amplitude: "+nf(currAmp,0,2),300,40);
		text("fps: "+nf(frameRate,0,2),300,70);
		text("[Left/Right] Gate thresh: " + nf(thresh,0,2), 300,100);
		text("[Up/Down] Gate release (#samples): " + nf(release,0,2), 300,130);
		
		stroke(0,255,0);
		strokeWeight(36);
		fill(0,255,0,60);
		// NOISE GATE
		if(currAmp > thresh) {
	        circle(800,300,100);
//	        println("above threshold");
	        // open gate
	        releaseCounter = 0;
	        capture = true;
		}else if(currAmp < thresh && releaseCounter<release){
	        // release on
	        stroke(0,30,180);
	        strokeWeight(36);  
	        fill(0,30,180,60);
	        circle(800,300,100);
	        releaseCounter++;
//	        println("release");
	        capture = true;
	    }else if(currAmp < thresh){
//	      println("under thresh");
	      capture = false;
	    }

		noFill();
		stroke(253,255,135,150);
		strokeWeight(6);
		beginShape();
		for(int i = 0; i < nsamples; i++)
		{
			vertex(
					map(i, 0, nsamples, 0, width),
					map(waveform.data[i], -1, 1, 0, height)
					);
		}
		endShape();

		if(capture) {
			stroke(253,253,255,150);
			noFill();
			fft.analyze(spectrum);
			for(int i = 0; i < bands; i++){

				/* the result of the FFT is normalized */
				/* draw the line for frequency band i scaling it up by 40 to get more amplitude */
				line( i*width/bands, height, i*width/bands, height - spectrum[i]*height*40);
				fftFeatures[i] = spectrum[i];
			} 
		}

		fill(255);
		textSize(20);

		if(mode.equals("Test")) {
			text("Current model: " + selectedModel, 20, 80);
			if (classifier != null) {
				String guessedLabel = classifier.classify(captureInstance(null));
				text("classified as: " + guessedLabel, 20, 110);
			}else {
				text("no classifier", 20,110);
			}
			
		}else {
			text("[.] Class: " + classNames[classIndex], 20, 80);
			dataCount = trainingData.get(classNames[classIndex]).size();
			text("[spacebar] Data collected: " + dataCount, 20, 110);
			text("[enter] Features: ", 20, 140);
		}
		

	}

	public void models(int index) {
		  selectedModel = (String) cp5.get(ScrollableList.class, "models").getItem(index).get("name");
		  String cwd = System.getProperty("user.dir");
		  try {
			  // load selectedModel
			  classifier = (MLClassifier) weka.core.SerializationHelper.read(cwd + "/"+ selectedModel);
			  System.out.println("Loaded: " + selectedModel);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("Loading ERROR");
				e.printStackTrace();
			}
		}
	
	public void keyPressed() {
		if (key == '.') {
			classIndex = (classIndex + 1) % classNames.length;
		}
		
		else if (key == 't') {
			if(classifier == null) {
				println("Start training ...");
				classifier = new MLClassifier();
				classifier.train(trainingData);
			}else {
				classifier = null;
			}
		}
		
		else if (key == 's') {
			// Yang: add code to save your trained model for later use
			try {
				String cwd = System.getProperty("user.dir");
		        System.out.println(cwd);
		        String fname;
				File f = new File(cwd);

		        String[] pathnames = f.list();
		        int largestidx = 0;
		        for (String pathname: pathnames) {
		        	String dummy = pathname.replaceAll("[^0-9]", "");
		        	if (dummy.isEmpty()) {
		        		continue;
		        	}
		        	int idx = Integer.valueOf(dummy);
		        	if(idx>largestidx) {
		        		largestidx = idx;
		        	}
		        }
		        largestidx ++;
		        fname = "demo_" + String.valueOf(largestidx) + ".model";
		        System.out.println("Saving -currently hardcoded : " + cwd + "/"+fname);
		        
				weka.core.SerializationHelper.write(fname, classifier);
		        
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("SAVING ERROR");
				e.printStackTrace();
			}
		}
		
		else if (key == 'l') {
			// Yang: add code to load your previously trained model
			
			
			// create options to toggle numbers
			System.out.println("loading model: " + modelname_root);
			try {
				String cwd = System.getProperty("user.dir");
				classifier = (MLClassifier) weka.core.SerializationHelper.read(cwd + "/demo_1.model");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("ERROR LOADING MODEL");
				e.printStackTrace();
			}
		}
		
		else if (key == ' ' && capture) {
			trainingData.get(classNames[classIndex]).add(captureInstance(classNames[classIndex]));
		}
		
		else if (keyCode == UP) {
			if(release>=0 && release <200) {
				release += 10;
			}
		}
		else if (keyCode == DOWN) {
			if(release>0 && release <= 210) {
				release -= 10;
			}
		}
		else if (keyCode == LEFT) {
			if(thresh>0.0f && thresh <= 1.1f) {
				thresh -= 0.05f;
			}
		}
		else if (keyCode == RIGHT) {
			if(thresh>=-1.0f && thresh < 1.0f) {
				thresh += 0.05f;
			}
		}
		else if (keyCode == ENTER) {
			
		}
			
	}


}
