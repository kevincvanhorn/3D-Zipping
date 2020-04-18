/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     https://creativecommons.org/publicdomain/zero/1.0/
 */

import juicer.Geometry;


import java.io.File;

//import net.imagej.plugins.comands.display.interactive.Threshold;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import org.scijava.convert.ConvertService;
import net.imagej.ImgPlus;

/**
 * A {@link Command} plugin for opening and displaying a .juice file.
 */
@Plugin(type = Command.class, menuPath = "Juicer > Juice")
public class Juicer<T extends RealType<T>> implements Command {
	
	@Parameter
    private ConvertService convertService;
	
	@Parameter
	private OpService ops;
	
	@Parameter
	private Img<T> image;

	@Parameter(type = ItemIO.OUTPUT)
	private Img<FloatType> result;	
	
	/** The run method executes the command. */
	@Override
	public void run() {
		ImagePlus cimp = IJ.getImage();
		juice(cimp);
	}
	
	void juice(ImagePlus imp) {
		int type = imp.getType();
		
		if(type == ImagePlus.GRAY8 || type == ImagePlus.GRAY16) {				
			ImgPlus img = ImagePlusAdapter.wrapImgPlus(imp); // https://gist.github.com/GenevieveBuckley/460d0abc7c1b13eee983187b955330ba
			Geometry<T> geometry = new Geometry<T>(ops, img, convertService);
			geometry.run();
		}
		else {
			System.out.println("INVALID image type");
		}
	}

	/** The main method enables standalone testing of the command. */
	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ instance = new ImageJ();
		
		// display the user interface
		instance.ui().showUI();

		// open and display an image
		//final File imageFile = instance.ui().chooseFile(null, FileWidget.OPEN_STYLE);
		final Dataset image = instance.scifio().datasetIO().open("C:/Users/kevin/Desktop/test_01.tif");
		instance.ui().show(image);
		
		// execute the filter, waiting for the operation to finish.
		instance.command().run(Juicer.class, true).get().getOutput("result");
	}
}
