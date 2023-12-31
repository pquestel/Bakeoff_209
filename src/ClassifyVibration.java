import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import processing.core.PApplet;
import processing.sound.AudioIn;
import processing.sound.FFT;
import processing.sound.Sound;
import processing.sound.Waveform;

public class ClassifyVibration extends PApplet {

	FFT fft;
	AudioIn in;
	Waveform waveform;
	int bands = 512;
	int nsamples = 1024;
	float[] spectrum = new float[bands];
	float[] fftFeatures = new float[bands];
	String[] classNames = {"neutral", "full", "empty"};
	int classIndex = 0;
	int dataCount = 0;
	boolean isRecording = false;
	List<String> classificationList = new ArrayList<>();
	boolean moreTraining = false;
	File path = new File("C:\\Users\\phili\\OneDrive\\Desktop\\Electrical Engineering MS - UCLA\\Fall 2023\\209AS - Engineering Interactive Systems\\Bakeoff Project\\Models");


	MLClassifier classifier;

	Map<String, List<DataInstance>> trainingData = new HashMap<>();
	{
		for (String className : classNames) {
			trainingData.put(className, new ArrayList<DataInstance>());
		}
	}

	DataInstance captureInstance(String label) {
		DataInstance res = new DataInstance();
		res.label = label;
		res.measurements = fftFeatures.clone();
		return res;
	}

	public static void main(String[] args) {
		PApplet.main("ClassifyVibration");
	}

	public void settings() {
		size(512, 400);
	}

	public void setup() {
		Sound.list();
		Sound s = new Sound(this);
		s.inputDevice(2);
		fft = new FFT(this, bands);
		in = new AudioIn(this, 0);
		waveform = new Waveform(this, nsamples);
		waveform.input(in);
		in.start();
		fft.input(in);
	}

	public void draw() {
		background(0);
		fill(0);
		stroke(255);
		waveform.analyze();

		beginShape();
		for (int i = 0; i < nsamples; i++) {
			vertex(
					map(i, 0, nsamples, 0, width),
					map(waveform.data[i], -1, 1, 0, height)
			);
		}
		endShape();

		fft.analyze(spectrum);
		for (int i = 0; i < bands; i++) {
			line(i, height, i, height - spectrum[i] * height * 40);
			fftFeatures[i] = spectrum[i];
		}

		fill(255);
		textSize(30);
		if (classifier != null&& moreTraining == false) {
			String guessedLabel = classifier.classify(captureInstance(null));

			if (isRecording) {
				classificationList.add(guessedLabel);
			}

			text("classified as: " + guessedLabel, 20, 30);
		} else {
			text(classNames[classIndex], 20, 30);
			dataCount = trainingData.get(classNames[classIndex]).size();
			text("Data collected: " + dataCount, 20, 60);
		}
	}

	public void keyPressed() {
		if (key == CODED && keyCode == DOWN) {
			classIndex = (classIndex + 1) % classNames.length;
		} else if (key == ' ') {
			if (isRecording) {
				isRecording = false;
				println("Recording stopped.");
				outputClassifications();
			} else {
				isRecording = true;
				classificationList.clear();
				println("Recording started.");
			}
		} else if (key == 't') {
			if (classifier == null) {
				println("Start training ...");
				classifier = new MLClassifier();
				classifier.train(trainingData);
				moreTraining = false;
			}

			else if(classifier != null) {
				classifier.train(trainingData);
				moreTraining = false;
			}
			else {
				classifier = null;
			}
		} else if (key == 's') {
			// Placeholder for saving the trained model
			try {
				saveModel(classifier, "model1");
				println("Model saved successfully!");
				saveTrainingData(trainingData, "data1");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (key == 'l') {
			// Placeholder for loading the trained model
			try {
				classifier = loadModel("model1");
				println("Model loaded successfully!");
				trainingData = loadTrainingData("data1");
				println(trainingData.get("neutral").size());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		else if (key == 'm') {
		moreTraining = true;

		}
		else {
			trainingData.get(classNames[classIndex]).add(captureInstance(classNames[classIndex]));
		}
	}

	public void outputClassifications() {
		String mostFrequent = mostFrequentClassification();
		if (mostFrequent != null) {
			println("Most frequent classification: " + mostFrequent);
		}
		classificationList.clear();  // Clear the list for the next recording session
	}

	public String mostFrequentClassification() {
		Map<String, Integer> frequencyMap = new HashMap<>();
		for (String classification : classificationList) {
			frequencyMap.put(classification, frequencyMap.getOrDefault(classification, 0) + 1);
		}

		// If there are other classifications apart from "neutral", remove "neutral"
		if (frequencyMap.size() > 1 && frequencyMap.containsKey("neutral")) {
			frequencyMap.remove("neutral");
		}

		Map.Entry<String, Integer> mostFrequentEntry = null;
		for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
			if (mostFrequentEntry == null || entry.getValue().compareTo(mostFrequentEntry.getValue()) > 0) {
				mostFrequentEntry = entry;
			}
		}

		return mostFrequentEntry != null ? mostFrequentEntry.getKey() : null;
	}

	public void saveModel(MLClassifier classifier, String name) throws Exception {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path + name + ".model"));
		oos.writeObject(classifier);
		oos.flush();
		oos.close();
	}

	public MLClassifier loadModel(String name) throws Exception {
		FileInputStream fis = new FileInputStream(path + name + ".model");
		ObjectInputStream ois = new ObjectInputStream(fis);
		MLClassifier loadedClassifier = (MLClassifier) ois.readObject();
		ois.close();


		return loadedClassifier;
	}


	// Define a method to save the training data to a file
	public void saveTrainingData(Map<String, List<DataInstance>> trainingData, String dataName) {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path + dataName + ".data"))) {
			oos.writeObject(trainingData);
			println("Training data saved successfully!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Define a method to load the training data from a file
	public Map<String, List<DataInstance>> loadTrainingData(String dataName) {
		try {
			FileInputStream dataFile = new FileInputStream(path + dataName + ".data");
			ObjectInputStream dataStream = new ObjectInputStream(dataFile);
			@SuppressWarnings("unchecked")
			Map<String, List<DataInstance>> loadedTrainingData = (Map<String, List<DataInstance>>) dataStream.readObject();
			dataStream.close();
			println("Training data loaded successfully!");
			return loadedTrainingData;
		} catch (Exception e) {
			e.printStackTrace();
			return new HashMap<>(); // Return an empty map on error
		}
	}
}
