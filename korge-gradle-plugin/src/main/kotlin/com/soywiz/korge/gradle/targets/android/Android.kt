package com.soywiz.korge.gradle.targets.android

import com.soywiz.korge.gradle.*
import com.soywiz.korge.gradle.targets.*
import com.soywiz.korge.gradle.util.*
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.GradleBuild
import java.io.File
import kotlin.collections.LinkedHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.listOf
import kotlin.collections.set

//Linux: ~/Android/Sdk
//Mac: ~/Library/Android/sdk
//Windows: %LOCALAPPDATA%\Android\sdk
val Project.androidSdkPath: String by lazy {
	val userHome = System.getProperty("user.home")
	listOfNotNull(
		System.getenv("ANDROID_HOME"),
		"$userHome/AppData/Local/Android/sdk",
		"$userHome/Library/Android/sdk",
		"$userHome/Android/Sdk"
	).firstOrNull { File(it).exists() } ?: error("Can't find android sdk (ANDROID_HOME environment not set and Android SDK not found in standard locations)")
}

fun Project.configureNativeAndroid() {
	val resolvedArtifacts = LinkedHashMap<String, String>()

	configurations.all {
		it.resolutionStrategy.eachDependency {
			val cleanFullName = "${it.requested.group}:${it.requested.name}".removeSuffix("-js").removeSuffix("-jvm")
			//println("RESOLVE ARTIFACT: ${it.requested}")
			//if (cleanFullName.startsWith("org.jetbrains.intellij.deps:trove4j")) return@eachDependency
			//if (cleanFullName.startsWith("org.jetbrains:annotations")) return@eachDependency
			if (cleanFullName.startsWith("org.jetbrains")) return@eachDependency
			if (cleanFullName.startsWith("junit:junit")) return@eachDependency
			if (cleanFullName.startsWith("org.hamcrest:hamcrest-core")) return@eachDependency
			if (cleanFullName.startsWith("org.jogamp")) return@eachDependency
			resolvedArtifacts[cleanFullName] = it.requested.version.toString()
		}
	}

	//val androidPackageName = "com.example.myapplication"
	//val androidAppName = "My Awesome APP Name"
	val prepareAndroidBootstrap = tasks.create("prepareAndroidBootstrap") { task ->
		task.dependsOn("compileTestKotlinJvm") // So artifacts are resolved
		task.apply {
			val overwrite = korge.overwriteAndroidFiles
			val outputFolder = File(buildDir, "platforms/android")
			doLast {
				val androidPackageName = korge.id
				val androidAppName = korge.name

				val DOLLAR = "\\$"
				val ifNotExists = !overwrite
				//File(outputFolder, "build.gradle").conditionally(ifNotExists) {
				//	ensureParents().writeText("""
				//		// Top-level build file where you can add configuration options common to all sub-projects/modules.
				//		buildscript {
				//			repositories { google(); jcenter() }
				//			dependencies { classpath 'com.android.tools.build:gradle:3.3.0'; classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion" }
				//		}
				//		allprojects {
				//			repositories {
				//				mavenLocal(); maven { url = "https://dl.bintray.com/korlibs/korlibs" }; google(); jcenter()
				//			}
				//		}
				//		task clean(type: Delete) { delete rootProject.buildDir }
				//""".trimIndent())
				//}
				File(outputFolder, "local.properties").conditionally(ifNotExists) {
					ensureParents().writeText("sdk.dir=${androidSdkPath.escape()}")
				}
				File(outputFolder, "settings.gradle").conditionally(ifNotExists) { ensureParents().writeText("enableFeaturePreview(\"GRADLE_METADATA\")") }
				File(
					outputFolder,
					"proguard-rules.pro"
				).conditionally(ifNotExists) { ensureParents().writeText("#Rules here\n") }

				outputFolder["gradle"].mkdirs()
				rootDir["gradle"].copyRecursively(outputFolder["gradle"], overwrite = true) { f, e -> OnErrorAction.SKIP }

				File(outputFolder, "build.gradle").conditionally(ifNotExists) {
					ensureParents().writeText(Indenter {
						line("buildscript") {
							line("repositories { google(); jcenter(); maven { url = uri(\"https://dl.bintray.com/kotlin/kotlin-dev\") } }")
							line("dependencies { classpath 'com.android.tools.build:gradle:$androidBuildGradleVersion'; classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion' }")
						}
						line("repositories") {
							line("mavenLocal()")
							line("maven { url = 'https://dl.bintray.com/korlibs/korlibs' }")
							line("google()")
							line("jcenter()")
							line("maven { url = uri(\"https://dl.bintray.com/kotlin/kotlin-dev\") }")
						}

						if (korge.androidLibrary) {
							line("apply plugin: 'com.android.library'")
						} else {
							line("apply plugin: 'com.android.application'")
						}
						line("apply plugin: 'kotlin-android'")
						line("apply plugin: 'kotlin-android-extensions'")

						line("android") {
                            line("packagingOptions") {
                                line("exclude 'META-INF/DEPENDENCIES'")
                                line("exclude 'META-INF/LICENSE'")
                                line("exclude 'META-INF/LICENSE.txt'")
                                line("exclude 'META-INF/license.txt'")
                                line("exclude 'META-INF/NOTICE'")
                                line("exclude 'META-INF/NOTICE.txt'")
                                line("exclude 'META-INF/notice.txt'")
                                line("exclude 'META-INF/*.kotlin_module'")
								line("exclude '**/*.kotlin_metadata'")
								line("exclude '**/*.kotlin_builtins'")
                            }
							line("compileSdkVersion ${korge.androidCompileSdk}")
							line("defaultConfig") {
								if (korge.androidMinSdk < 21)
									line("multiDexEnabled true")

								if (!korge.androidLibrary) {
									line("applicationId '$androidPackageName'")
								}

								line("minSdkVersion ${korge.androidMinSdk}")
								line("targetSdkVersion ${korge.androidTargetSdk}")
								line("versionCode 1")
								line("versionName '1.0'")
//								line("buildConfigField 'boolean', 'FULLSCREEN', '${korge.fullscreen}'")
								line("testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'")
                                val manifestPlaceholdersStr = korge.configs.map { it.key + ":" + it.value.quoted }.joinToString(", ")
								line("manifestPlaceholders = ${if (manifestPlaceholdersStr.isEmpty()) "[:]" else "[$manifestPlaceholdersStr]" }")
							}
							line("signingConfigs") {
								line("release") {
									line("storeFile file(findProperty('RELEASE_STORE_FILE') ?: 'korge.keystore')")
									line("storePassword findProperty('RELEASE_STORE_PASSWORD') ?: 'password'")
									line("keyAlias findProperty('RELEASE_KEY_ALIAS') ?: 'korge'")
									line("keyPassword findProperty('RELEASE_KEY_PASSWORD') ?: 'password'")
								}
							}
							line("buildTypes") {
								line("debug") {
									line("minifyEnabled false")
									line("signingConfig signingConfigs.release")
								}
								line("release") {
									//line("minifyEnabled false")
									line("minifyEnabled true")
									line("proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'")
									line("signingConfig signingConfigs.release")
								}
							}
							line("sourceSets") {
								line("main") {
									// @TODO: Use proper source sets of the app

									val projectDir = project.projectDir
									line("java.srcDirs += [${"$projectDir/src/commonMain/kotlin".quoted}, ${"$projectDir/src/androidMain/kotlin".quoted}]")
									line("assets.srcDirs += [${"$projectDir/src/commonMain/resources".quoted}, ${"$projectDir/src/androidMain/resources".quoted}, ${"$projectDir/build/genMainResources".quoted}]")
								}
							}
						}

						line("dependencies") {
							line("implementation fileTree(dir: 'libs', include: ['*.jar'])")
							line("implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion'")
							if (korge.androidMinSdk < 21)
								line("implementation 'com.android.support:multidex:1.0.3'")

							//line("api 'org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion'")
							for ((name, version) in resolvedArtifacts) {
								if (name.startsWith("org.jetbrains.kotlin")) continue
								if (name.contains("-metadata")) continue
                                //if (name.startsWith("com.soywiz.korlibs.krypto:krypto")) continue
                                //if (name.startsWith("com.soywiz.korlibs.korge:korge")) {
								if (name.startsWith("com.soywiz.korlibs.")) {
									val rversion = getModuleVersion(name, version)
                                    line("implementation '$name-android:$rversion'")
                                }
							}

							for (dependency in korge.plugins.pluginExts.flatMap { it.getAndroidDependencies() }) {
								line("implementation ${dependency.quoted}")
							}

							line("implementation 'com.android.support:appcompat-v7:28.0.0'")
							line("implementation 'com.android.support.constraint:constraint-layout:1.1.3'")
							line("testImplementation 'junit:junit:4.12'")
							line("androidTestImplementation 'com.android.support.test:runner:1.0.2'")
							line("androidTestImplementation 'com.android.support.test.espresso:espresso-core:3.0.2'")
						}

						line("configurations") {
							line("androidTestImplementation.extendsFrom(commonMainApi)")
						}
					}.toString())
				}

				writeAndroidManifest(outputFolder, korge)

				File(outputFolder, "gradle.properties").conditionally(ifNotExists) {
					ensureParents().writeText("org.gradle.jvmargs=-Xmx1536m")
				}
			}
		}
	}

	val bundleAndroid = tasks.create("bundleAndroid", GradleBuild::class.java) { task ->
		task.apply {
			group = GROUP_KORGE_INSTALL
			dependsOn(prepareAndroidBootstrap)
			buildFile = File(buildDir, "platforms/android/build.gradle")
			version = "4.10.1"
			tasks = listOf("bundleDebugAar")
		}
	}

	val buildAndroidAar = tasks.create("buildAndroidAar", GradleBuild::class.java) { task ->
		task.dependsOn(bundleAndroid)
	}

	// adb shell am start -n com.package.name/com.package.name.ActivityName
	for (debug in listOf(false, true)) {
		val suffixDebug = if (debug) "Debug" else "Release"
		val installAndroidTask = tasks.create("installAndroid$suffixDebug", GradleBuild::class.java) { task ->
			task.apply {
				group = GROUP_KORGE_INSTALL
				dependsOn(prepareAndroidBootstrap)
				buildFile = File(buildDir, "platforms/android/build.gradle")
				version = "4.10.1"
				tasks = listOf("install$suffixDebug")
			}
		}

		for (emulator in listOf(null, false, true)) {
			val suffixDevice = when (emulator) {
				null -> ""
				false -> "Device"
				true -> "Emulator"
			}

			val extra = when (emulator) {
				null -> arrayOf()
				false -> arrayOf("-d")
				true -> arrayOf("-e")
			}

			tasks.createTyped<DefaultTask>("runAndroid$suffixDevice$suffixDebug") {
				group = GROUP_KORGE_RUN
				dependsOn(installAndroidTask)
				doFirst {
					execLogger {
						it.commandLine(
							"$androidSdkPath/platform-tools/adb", *extra, "shell", "am", "start", "-n",
							"${korge.id}/${korge.id}.MainActivity"
						)
					}
				}
			}
		}
	}
}

fun writeAndroidManifest(outputFolder: File, korge: KorgeExtension) {
	val androidPackageName = korge.id
	val androidAppName = korge.name
	val ifNotExists = korge.overwriteAndroidFiles
	File(outputFolder, "src/main/AndroidManifest.xml").also { it.parentFile.mkdirs() }.conditionally(ifNotExists) {
		ensureParents().writeText(Indenter {
			line("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
			line("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$androidPackageName\">")
			indent {
				line("<application")
				indent {
					line("")
					line("android:allowBackup=\"true\"")

					if (!korge.androidLibrary) {
						line("android:label=\"$androidAppName\"")
						line("android:icon=\"@mipmap/icon\"")
						// // line("android:icon=\"@android:drawable/sym_def_app_icon\"")
						line("android:roundIcon=\"@android:drawable/sym_def_app_icon\"")
						line("android:theme=\"@android:style/Theme.Holo.NoActionBar\"")
					}


					line("android:supportsRtl=\"true\"")
				}
				line(">")
				indent {
					for (text in korge.plugins.pluginExts.mapNotNull { it.getAndroidManifestApplication() }) {
						line(text)
					}
					for (text in korge.androidManifestApplicationChunks) {
						line(text)
					}

					line("<activity android:name=\".MainActivity\"")
					indent {
						when (korge.orientation) {
							Orientation.LANDSCAPE -> line("android:screenOrientation=\"landscape\"")
							Orientation.PORTRAIT -> line("android:screenOrientation=\"portrait\"")
						}
					}
					line(">")

					if (!korge.androidLibrary) {
						indent {
							line("<intent-filter>")
							indent {
								line("<action android:name=\"android.intent.action.MAIN\"/>")
								line("<category android:name=\"android.intent.category.LAUNCHER\"/>")
							}
							line("</intent-filter>")
						}
					}
					line("</activity>")
				}
				line("</application>")
				for (text in korge.androidManifestChunks) {
					line(text)
				}
			}
			line("</manifest>")
		}.toString())
	}
	File(outputFolder, "korge.keystore").conditionally(ifNotExists) {
		ensureParents().writeBytes(getResourceBytes("korge.keystore"))
	}
	File(outputFolder, "src/main/res/mipmap-mdpi/icon.png").conditionally(ifNotExists) {
		ensureParents().writeBytes(korge.getIconBytes())
	}
	File(outputFolder, "src/main/java/MainActivity.kt").conditionally(ifNotExists) {
		ensureParents().writeText(Indenter {
			line("package $androidPackageName")

			line("import com.soywiz.korio.android.withAndroidContext")
			line("import com.soywiz.korgw.KorgwActivity")
			line("import ${korge.realEntryPoint}")

			line("class MainActivity : KorgwActivity()") {
				line("override suspend fun activityMain()") {
					//line("withAndroidContext(this)") { // @TODO: Probably we should move this to KorgwActivity itself
						for (text in korge.plugins.pluginExts.mapNotNull { it.getAndroidInit() }) {
							line(text)
						}
						line("${korge.realEntryPoint}()")
					//}
				}
			}
		}.toString())
	}
}

val tryAndroidSdkDirs = listOf(
	File(System.getProperty("user.home"), "/Library/Android/sdk"), // MacOS
	File(System.getProperty("user.home"), "/Android/Sdk"), // Linux
	File(System.getProperty("user.home"), "/AppData/Local/Android/Sdk") // Windows
)

val prop_sdk_dir = System.getProperty("sdk.dir")
val prop_ANDROID_HOME = System.getenv("ANDROID_HOME")
var hasAndroidConfigured = ((prop_sdk_dir != null) || (prop_ANDROID_HOME != null))

fun Project.tryToDetectAndroidSdkPath(): File? {
	for (tryAndroidSdkDirs in tryAndroidSdkDirs) {
		if (tryAndroidSdkDirs.exists()) {
			return tryAndroidSdkDirs.absoluteFile
		}
	}
	return null
}
