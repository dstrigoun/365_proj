package application;


import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;


public class Main extends Application {
	@FXML
	private ImageView imageView;
	
	@Override
	public void start(Stage primaryStage) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
			BorderPane root = (BorderPane)loader.load();
			Scene scene = new Scene(root, 600, 400);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.setTitle("Blind Helper");
			primaryStage.show();
			primaryStage.setWidth(740);
			primaryStage.setHeight(558);
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
		          public void handle(WindowEvent we) {
		        	  // same code for handling volume control in Controller
		              Mixer.Info [] mixers = AudioSystem.getMixerInfo();  
						for (Mixer.Info mixerInfo : mixers){
						    Mixer mixer = AudioSystem.getMixer(mixerInfo);
						    Line.Info [] lineInfos = mixer.getTargetLineInfo();
						    for (Line.Info lineInfo : lineInfos){
						        Line line = null;  
						        boolean opened = true;  
						        try {
						            line = mixer.getLine(lineInfo);  
						            opened = line.isOpen() || line instanceof Clip;
						            if (!opened)    
						                line.open();
						            FloatControl volCtrl = (FloatControl)line.getControl(FloatControl.Type.VOLUME);  
						            volCtrl.setValue(0.0f);
						        }  
						        catch (LineUnavailableException e) {  
						            e.printStackTrace();  
						        }  
						        catch (IllegalArgumentException iaEx) {  
						        }  
						        finally {  
						            if (line != null && !opened) 
						                line.close();
						        }  
						    }
						}
		          }
		      });
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		launch(args);
	}
}
