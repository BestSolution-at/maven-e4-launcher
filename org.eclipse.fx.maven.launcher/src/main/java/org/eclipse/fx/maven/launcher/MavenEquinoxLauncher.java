package org.eclipse.fx.maven.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.equinox.launcher.Main;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MavenEquinoxLauncher {
	private static String MAVEN_ROOT = "/Users/tomschindl/.m2/repository";

	public static void main(String[] args) throws ParseException, IOException {
		Options options = new Options();
		options.addOption("elc_file", true, "e(fx)clipse Launch Configuration");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		JsonParser p = new JsonParser();
		JsonElement root = p.parse(new FileReader(new File(cmd.getOptionValue("elc_file"))));

		JsonObject jsonObject = root.getAsJsonObject();

		String configIni = generateConfig(jsonObject);

		JsonArray argArray = jsonObject.get("framework").getAsJsonObject().get("arguments").getAsJsonArray();

		String[] arguments = new String[argArray.size()+2];
		arguments[0] = "-configuration";
		arguments[1] = "file:" + configIni;
		for( int i = 0; i < argArray.size(); i++ ) {
			arguments[i+2] = argArray.get(i).getAsString();
		}

//		System.getProperties().put("eclipse.ignoreApp", "true");
//		System.getProperties().put("osgi.noShutdown", "true");
		System.getProperties().put("org.osgi.framework.bundle.parent","ext");
		Main.main(arguments);
	}

	private static String generateConfig(JsonObject jsonObject) throws IOException {
		File folder = new File("/tmp/"+jsonObject.get("name").getAsString()+"/configuration");
		folder.mkdirs();

		final String LF = System.getProperty("line.separator");

		File rv = new File(folder,"config.ini");

		FileWriter w = new FileWriter(rv);
		JsonArray modules = jsonObject.get("modules").getAsJsonArray();
		JsonObject simpleConfigurator = null;

		for( JsonElement e : modules ) {
			if( e.getAsJsonObject().get("name") != null && "org.eclipse.equinox.simpleconfigurator".equals(e.getAsJsonObject().get("name").getAsString()) ) {
				simpleConfigurator = e.getAsJsonObject();
			}
		}

		if( simpleConfigurator != null ) {
			File simpleConfiguratorFolder = new File(folder,"org.eclipse.equinox.simpleconfigurator");
			simpleConfiguratorFolder.mkdirs();
			w.append("osgi.bundles="+toReferenceURL(simpleConfigurator));
			w.append(LF);
			w.append("osgi.bundles.defaultStartLevel=4");
			w.append(LF);
			w.append("osgi.install.area=file\\:/tmp/"+jsonObject.get("name").getAsString()+"/install");
			w.append(LF);
			JsonObject framework = jsonObject.get("framework").getAsJsonObject();
			w.append("osgi.framework="+ framework.get("url").getAsString().replace(":", "\\:"));
			w.append(LF);
			w.append("eclipse.p2.data.area=@config.dir/.p2");
			w.append(LF);
			w.append("org.eclipse.equinox.simpleconfigurator.configUrl=file\\:"+simpleConfiguratorFolder.getAbsolutePath()+"/bundles.info");
			w.append(LF);
			w.append("osgi.configuration.cascaded=false");
			w.append(LF);
			w.close();

			FileWriter bundleInfo = new FileWriter(new File(simpleConfiguratorFolder,"bundles.info"));
			bundleInfo.append("#encoding=UTF-8");
			bundleInfo.append(LF);
			bundleInfo.append("#version=1");
			bundleInfo.append(LF);

			for( JsonElement e : jsonObject.get("modules").getAsJsonArray() ) {
				JsonObject module = e.getAsJsonObject();
				String name = module.has("name") ? module.get("name").getAsString() : module.get("artifactId").getAsString();

				if( name.equals("org.eclipse.osgi") ) {
					continue;
				}
				bundleInfo.append(name);
				bundleInfo.append("," + getOSGiVersion(toFileURL(module).substring("file:".length())));
				bundleInfo.append("," + toFileURL(module));
				bundleInfo.append("," + (module.has("startLevel") ? module.get("startLevel").getAsInt() : "4"));
				bundleInfo.append("," + (module.has("startLevel") ? true : false));
				bundleInfo.append(LF);
			}

			bundleInfo.close();


			// w.append("osgi.splashPath=file\\:/Users/tomschindl/Documents/e-workspaces/efxclipse-master-maven/my.pde.sample.app");
		} else {
			w.append("osgi.bundles=");
			for( int i = 0; i < modules.size(); i++ ) {
				if( i > 0 ) {
					w.append(",");
				}
				w.append(toReferenceURL(modules.get(i).getAsJsonObject()));
			}

			w.append(LF);

			w.append("osgi.bundles.defaultStartLevel=4");
			w.append(LF);

			w.append("osgi.install.area=file\\:/tmp/"+jsonObject.get("name").getAsString()+"/install");
			w.append(LF);

			JsonObject framework = jsonObject.get("framework").getAsJsonObject();
			w.append("osgi.framework="+ framework.get("url").getAsString().replace(":", "\\:"));
			w.append(LF);

			w.append("osgi.configuration.cascaded=false");
			w.append(LF);

			w.close();
		}

		return folder.getAbsolutePath();
	}

	private static String toReferenceURL(JsonObject element) {
		StringBuilder w = new StringBuilder();
		if( element.get("type").getAsString().equals("maven") ) {
			String groupId = element.get("groupId").getAsString();
			String artifactId = element.get("artifactId").getAsString();
			String version = element.get("version").getAsString();
			w.append("reference\\:file\\:" + MAVEN_ROOT + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar" );
		} else {
			w.append("reference\\:" + element.get("url").getAsString().replace(":", "\\:"));
		}

		if( element.has("startLevel") ) {
			w.append("@" + element.get("startLevel").getAsInt() + "\\:start");
		} else {
			w.append("@start");
		}
		return w.toString();
	}

	private static String toFileURL(JsonObject element) {
		if( element.get("type").getAsString().equals("maven") ) {
			String groupId = element.get("groupId").getAsString();
			String artifactId = element.get("artifactId").getAsString();
			String version = element.get("version").getAsString();
			return "file:" + MAVEN_ROOT + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
		} else {
			return "file:" + element.get("url").getAsString();
		}
	}

	private static String getOSGiVersion(String path) throws IOException {
		if( path.endsWith(".jar") ) {
			try(JarFile f = new JarFile(path)) {
				Manifest manifest = f.getManifest();
				String rv = manifest.getMainAttributes().getValue("Bundle-Version");
				f.close();
				return rv;
			}
		} else {
			try( InputStream is = new FileInputStream(path+"/META-INF/MANIFEST.MF") ) {
				Manifest manifest = new Manifest(is);
				return manifest.getMainAttributes().getValue("Bundle-Version");
			}

		}
	}
}
