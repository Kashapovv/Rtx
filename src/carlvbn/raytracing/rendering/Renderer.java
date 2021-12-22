package carlvbn.raytracing.rendering;

import carlvbn.raytracing.pixeldata.Color;
import carlvbn.raytracing.pixeldata.GaussianBlur;
import carlvbn.raytracing.pixeldata.PixelBuffer;
import carlvbn.raytracing.pixeldata.PixelData;
import carlvbn.raytracing.math.*;
import carlvbn.raytracing.solids.Solid;

import java.awt.Graphics;

public class Renderer {
    private static final float GLOBAL_ILLUMINATION = 0.3F;
    private static final float SKY_EMISSION = 0.5F;
    private static final int MAX_REFLECTION_BOUNCES = 5;
    private static final boolean SHOW_SKYBOX = true;

    public static float bloomIntensity = 0.5F;
    public static int bloomRadius = 10;


    public static PixelBuffer renderScene(Scene scene, int width, int height) {
        PixelBuffer pixelBuffer = new PixelBuffer(width, height);

        for (int x = 0; x<width; x++) {
            for (int y = 0; y<height; y++) {
                float[] screenUV = getNormalizedScreenCoordinates(x, y, width, height);

                pixelBuffer.setPixel(x, y, computePixelInfo(scene, screenUV[0], screenUV[1]));
            }
        }

        return pixelBuffer;
    }


    public static void renderScene(Scene scene, Graphics gfx, int width, int height, float resolution) {
        int blockSize = (int) (1/resolution);

        for (int x = 0; x<width; x+=blockSize) {
            for (int y = 0; y<height; y+=blockSize) {
                float[] uv = getNormalizedScreenCoordinates(x, y, width, height);
                PixelData pixelData = computePixelInfo(scene, uv[0], uv[1]);

                gfx.setColor(pixelData.getColor().toAWTColor());
                gfx.fillRect(x, y, blockSize, blockSize);
            }
        }
    }


    public static void renderScenePostProcessed(Scene scene, Graphics gfx, int width, int height, float resolution) {
        int bufferWidth = Math.round(width*resolution+0.49F);
        int bufferHeight = Math.round(height*resolution+0.49F);
        PixelBuffer pixelBuffer  = renderScene(scene, bufferWidth, bufferHeight);

        PixelBuffer emissivePixels = pixelBuffer.clone();
        emissivePixels.filterByEmission(0.1F);
        GaussianBlur blur = new GaussianBlur(emissivePixels);
        blur.blur(bloomRadius, 1);
        pixelBuffer.add(blur.getPixelBuffer().multiply(bloomIntensity).resize(bufferWidth, bufferHeight, true));

        float blockSize = 1/resolution;
        for (int x = 0; x<bufferWidth; x++) {
            for (int y = 0; y < bufferHeight; y++) {
                PixelData pixel = pixelBuffer.getPixel(x, y);
                gfx.setColor(pixel.getColor().toAWTColor());
                gfx.fillRect((int)(x*blockSize), (int)(y*blockSize), (int)blockSize+1, (int)blockSize+1);
            }
        }

    }


    public static float[] getNormalizedScreenCoordinates(int x, int y, int width, int height) {
        float u = 0, v = 0;
        if (width > height) {
            u = (float)(x - width/2+height/2) / height * 2 - 1;
            v =  -((float) y / height * 2 - 1);
        } else {
            u = (float)x / width * 2 - 1;
            v =  -((float) (y - height/2+width/2) / width * 2 - 1);
        }

        return new float[]{u, v};
    }

    public static PixelData computePixelInfo(Scene scene, float u, float v) {
        Vector3 eyePos = new Vector3(0,0, (float)(-1/Math.tan(Math.toRadians(scene.getCamera().getFOV()/2))));
        Camera cam = scene.getCamera();

        Vector3 rayDir = new Vector3(u, v, 0).subtract(eyePos).normalize().rotateYP(cam.getYaw(), cam.getPitch());
        RayHit hit = scene.raycast(new Ray(eyePos.add(cam.getPosition()), rayDir));
        if (hit != null) {
            return computePixelInfoAtHit(scene, hit, MAX_REFLECTION_BOUNCES);
        } else if (SHOW_SKYBOX) {
            Color sbColor = scene.getSkybox().getColor(rayDir);
            return new PixelData(sbColor, Float.POSITIVE_INFINITY, sbColor.getLuminance()*SKY_EMISSION);
        } else {
            return new PixelData(Color.BLACK, Float.POSITIVE_INFINITY, 0);
        }
    }

    private static PixelData computePixelInfoAtHit(Scene scene, RayHit hit, int recursionLimit) {
        Vector3 hitPos = hit.getPosition();
        Vector3 rayDir = hit.getRay().getDirection();
        Solid hitSolid = hit.getSolid();
        Color hitColor = hitSolid.getTextureColor(hitPos.subtract(hitSolid.getPosition()));
        float brightness = getDiffuseBrightness(scene, hit);
        float specularBrightness = getSpecularBrightness(scene, hit);
        float reflectivity = hitSolid.getReflectivity();
        float emission = hitSolid.getEmission();

        PixelData reflection;
        Vector3 reflectionVector = rayDir.subtract(hit.getNormal().multiply(2*Vector3.dot(rayDir, hit.getNormal())));
        Vector3 reflectionRayOrigin = hitPos.add(reflectionVector.multiply(0.001F));
        RayHit reflectionHit = recursionLimit > 0 ? scene.raycast(new Ray(reflectionRayOrigin, reflectionVector)) : null;
        if (reflectionHit != null) {
            reflection = computePixelInfoAtHit(scene, reflectionHit, recursionLimit-1);
        } else {
            Color sbColor = scene.getSkybox().getColor(reflectionVector);
            reflection = new PixelData(sbColor, Float.POSITIVE_INFINITY, sbColor.getLuminance()*SKY_EMISSION);
        }

        Color pixelColor = Color.lerp(hitColor, reflection.getColor(), reflectivity)
                .multiply(brightness)
                .add(specularBrightness)
                .add(hitColor.multiply(emission))
                .add(reflection.getColor().multiply(reflection.getEmission()*reflectivity));

        return new PixelData(pixelColor, Vector3.distance(scene.getCamera().getPosition(), hitPos), Math.min(1, emission+reflection.getEmission()*reflectivity+specularBrightness));
    }

    private static float getDiffuseBrightness(Scene scene, RayHit hit) {
        Light sceneLight = scene.getLight();


        RayHit lightBlocker = scene.raycast(new Ray(sceneLight.getPosition(), hit.getPosition().subtract(sceneLight.getPosition()).normalize()));
        if (lightBlocker != null && lightBlocker.getSolid() != hit.getSolid()) {
            return GLOBAL_ILLUMINATION;
        } else {
            return Math.max(GLOBAL_ILLUMINATION, Math.min(1, Vector3.dot(hit.getNormal(), sceneLight.getPosition().subtract(hit.getPosition()))));
        }
    }

    private static float getSpecularBrightness(Scene scene, RayHit hit) {
        Vector3 hitPos = hit.getPosition();
        Vector3 cameraDirection = scene.getCamera().getPosition().subtract(hitPos).normalize();
        Vector3 lightDirection = hitPos.subtract(scene.getLight().getPosition()).normalize();
        Vector3 lightReflectionVector = lightDirection.subtract(hit.getNormal().multiply(2*Vector3.dot(lightDirection, hit.getNormal())));

        float specularFactor = Math.max(0, Math.min(1, Vector3.dot(lightReflectionVector, cameraDirection)));
        return (float) Math.pow(specularFactor, 2)*hit.getSolid().getReflectivity();
    }
}
