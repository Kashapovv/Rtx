package carlvbn.raytracing.gui;

import carlvbn.raytracing.math.*;
import carlvbn.raytracing.pixeldata.Color;
import carlvbn.raytracing.rendering.*;
import carlvbn.raytracing.rendering.Renderer;
import carlvbn.raytracing.solids.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class Viewport extends JPanel {
    private JFrame frame;
    private JDialog settingsDialog;
    private Scene scene;
    private Camera camera;
    private Font font;
    private Vector3 cameraMotion;
    private float resolution;
    private Robot robot;
    private boolean captureCursor;
    private Cursor blankCursor;
    private float mouseSensitivity;
    private float movementSpeed;
    private boolean hideHUD;
    private boolean postProcessing;

    private BufferedImage frameBuffer;
    private long deltaTime;
    private float cameraYaw;
    private float cameraPitch;
    private Vector3 cameraPosition;

    public Viewport(JFrame container, JDialog settingsDialog) {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_D) {
                    cameraMotion.setX(0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_A) {
                    cameraMotion.setX(-0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_W) {
                    cameraMotion.setZ(0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_S) {
                    cameraMotion.setZ(-0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    cameraMotion.setY(0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    cameraMotion.setY(-0.2F);
                } else if (e.getKeyCode() == KeyEvent.VK_1) {
                    resolution = 1;
                } else if (e.getKeyCode() == KeyEvent.VK_2) {
                    resolution = 0.5F;
                } else if (e.getKeyCode() == KeyEvent.VK_3) {
                    resolution = 0.25F;
                } else if (e.getKeyCode() == KeyEvent.VK_4) {
                    resolution = 0.125F;
                } else if (e.getKeyCode() == KeyEvent.VK_H) {
                    hideHUD = !hideHUD;
                } else if (e.getKeyCode() == KeyEvent.VK_F1) {
                    settingsDialog.setVisible(true);
                    settingsDialog.setLocationRelativeTo(frame);
                    setCaptureCursor(false);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_D) {
                    cameraMotion.setX(0);
                } else if (e.getKeyCode() == KeyEvent.VK_A) {
                    cameraMotion.setX(0);
                } else if (e.getKeyCode() == KeyEvent.VK_W) {
                    cameraMotion.setZ(0);
                } else if (e.getKeyCode() == KeyEvent.VK_S) {
                    cameraMotion.setZ(0);
                } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    cameraMotion.setY(0);
                } else if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    cameraMotion.setY(0);
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setCaptureCursor(false);
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (captureCursor) {
                    int centerX = frame.getX() + frame.getWidth() / 2;
                    int centerY = frame.getY() + frame.getHeight() / 2;

                    int mouseXOffset = e.getXOnScreen() - centerX;
                    int mouseYOffset = e.getYOnScreen() - centerY;
                    cameraYaw = (cameraYaw + mouseXOffset * mouseSensitivity);
                    cameraPitch = (Math.min(90, Math.max(-90, cameraPitch + mouseYOffset * mouseSensitivity)));
                    robot.mouseMove(centerX, centerY);
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!captureCursor) setCaptureCursor(true);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                font = new Font("Consolas", Font.PLAIN, getWidth() / 50);
            }
        });

        this.frame = container;
        this.settingsDialog = settingsDialog;
        cameraMotion = new Vector3(0, 0, 0);
        resolution = 0.25F;
        mouseSensitivity = 0.1F;
        movementSpeed = 1F;
        captureCursor = false;
        hideHUD = false;

        scene = new Scene();
        camera = scene.getCamera();


        scene.addSolid(new Sphere(new Vector3(0, 0, 0), 0.4F, Color.GREEN, 0.4F, 0F));


        scene.addSolid(new Plane(-1F, new Color(0, 0, 0), true, 0.25F, 0F));

        this.cameraYaw = camera.getYaw();
        this.cameraPitch = camera.getPitch();
        this.cameraPosition = camera.getPosition();
        this.deltaTime = 1;

        try {
            robot = new Robot();
        } catch (Exception e) {
            e.printStackTrace();
        }


        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");
    }

    public void runMainLoop() {
        while (true) {
            long startTime = System.currentTimeMillis();
            if (cameraMotion.length() != 0) {
                cameraPosition.translate(cameraMotion.rotateYP(camera.getYaw(), 0).multiply(deltaTime / 50F * movementSpeed));
            }

            if (captureCursor) {
                camera.setYaw(cameraYaw);
                camera.setPitch(cameraPitch);
                camera.setPosition(cameraPosition);
            }

            BufferedImage tempBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            if (postProcessing)
                Renderer.renderScenePostProcessed(scene, tempBuffer.getGraphics(), getWidth(), getHeight(), resolution);
            else Renderer.renderScene(scene, tempBuffer.getGraphics(), getWidth(), getHeight(), resolution);
            frameBuffer = tempBuffer;

            repaint();

            deltaTime = System.currentTimeMillis() - startTime;
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        if (frameBuffer != null) g.drawImage(frameBuffer, 0, 0, this);

        if (!hideHUD) {
            g.setColor(java.awt.Color.WHITE);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            if (captureCursor) {
                String str1 = "";
                String str2 = "";
                Rectangle2D str1Bounds = fm.getStringBounds(str1, g);
                Rectangle2D str2Bounds = fm.getStringBounds(str2, g);
                g.drawString(str1, (int) (getWidth() - str1Bounds.getWidth()) - 10, (int) (getHeight() - (str1Bounds.getHeight()) / 2 - str2Bounds.getHeight()));
                g.drawString(str2, (int) (getWidth() - str2Bounds.getWidth()) - 10, (int) (getHeight() - str2Bounds.getHeight() / 2));
            } else {
                String str = "";
                Rectangle2D strBounds = fm.getStringBounds(str, g);
                g.drawString(str, (int) (getWidth() - strBounds.getWidth()) - 10, (int) (getHeight() - strBounds.getHeight() / 2));
            }

            String fps = String.valueOf(1000F / deltaTime);
            if (fps.length() > 4) fps = fps.substring(0, 4);
            String fpsString = "Framerate: " + fps + " FPS";
            String resolutionString = "Resolution: " + (resolution * 100) + "%";
            Rectangle2D str1Bounds = fm.getStringBounds(fpsString, g);
            Rectangle2D str2Bounds = fm.getStringBounds(resolutionString, g);
            g.drawString(fpsString, 10, (int) (str1Bounds.getHeight()));
            g.drawString(resolutionString, 10, (int) (str1Bounds.getHeight() + str2Bounds.getHeight()));

            String controlsStr1 = "";
            String controlsStr2 = "";
            str1Bounds = fm.getStringBounds(controlsStr1, g);
            str2Bounds = fm.getStringBounds(controlsStr2, g);
            g.drawString(controlsStr1, 10, (int) (getHeight() - (str1Bounds.getHeight()) / 2 - str2Bounds.getHeight()));
            g.drawString(controlsStr2, 10, (int) (getHeight() - str2Bounds.getHeight() / 2));


        }
    }

    private void setCaptureCursor(boolean captureCursor) {
        this.captureCursor = captureCursor;

        if (captureCursor) {
            if (settingsDialog.isVisible())
                settingsDialog.setVisible(false);

            setCursor(blankCursor);

            int centerX = frame.getX() + frame.getWidth() / 2;
            int centerY = frame.getY() + frame.getHeight() / 2;
            robot.mouseMove(centerX, centerY);
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }
}


