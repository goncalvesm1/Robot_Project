package org.firstinspires.ftc.teamcode.opModes;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.teamcode.robot.Drivetrain;
import org.firstinspires.ftc.teamcode.robot.ShelbyBot;
import org.firstinspires.ftc.teamcode.robot.SkyBot;
import org.firstinspires.ftc.teamcode.robot.TilerunnerMecanumBot;
import org.firstinspires.ftc.teamcode.util.Input_Shaper;
import org.firstinspires.ftc.teamcode.util.ManagedGamepad;

import java.util.Locale;

@TeleOp(name = "Mecanum")
//@Disabled
public class MecanumTeleop extends InitLinearOpMode
{
    private void initPreStart() {
        robot.setName(pmgr.getBotName());
        prevOpModeType = SkyBot.curOpModeType;
        ShelbyBot.curOpModeType = ShelbyBot.OpModeType.TELE;

        /* Initialize the hardware variables. */
        RobotLog.dd(TAG, "Initialize robot");
        //robot.init(this);
        boolean initSensors = prevOpModeType != ShelbyBot.OpModeType.AUTO;
        robot.init(this, initSensors);

        RobotLog.dd(TAG, "Initialize drivetrain");
        //robot.setDriveDir(ShelbyBot.DriveDir.INTAKE);
        dtrn.init(robot);

        dtrn.setRampUp(false);
        dtrn.setRampDown(false);

        RobotLog.dd(TAG, "Start Aend fHdg %.2f", robot.getAutonEndHdg());
        //RobotLog.dd(TAG, "Start Hdg %.2f", robot.get);
        RobotLog.dd(TAG, "Start Pos %s", robot.getAutonEndPos().toString());
        RobotLog.dd(TAG, "Start mode to %s", robot.leftMotor.getMode());
    }

    private void update()
    {
        int c = 0;
        for (DcMotorEx m : robot.motors.values())
        {
            cnts[c++] = m.getCurrentPosition();
        }

        hdg = robot.getGyroFhdg();
    }

    private void printTelem()
    {
        String cntStr = String.format(Locale.US,"CNTS: %d %d %d %d",
                cnts[0], cnts[1], cnts[2], cnts[3]);

        int l = 0;
        dashboard.displayPrintf(l++, "Dir %s", robot.getDriveDir());
        dashboard.displayText  (l++, cntStr);
        dashboard.displayText  (l++, robot.liftyBoi.toString());
        dashboard.displayPrintf(l++, "L_IN %4.2f L %4.2f", raw_lr_x, lr_x);
        dashboard.displayPrintf(l++, "R_IN %4.2f R %4.2f", raw_fb_y, fb_y);
        dashboard.displayPrintf(l,   "T_IN %4.2f T %4.2f", raw_turn, turn);
    }

    private void controlArmElev()
    {
        if(robot.liftyBoi == null) return;
        double  aelev       = -gpad2.value(ManagedGamepad.AnalogInput.L_STICK_Y);

        //aelev  = ishaper.shape(aelev);
        robot.liftyBoi.setLiftSpd(aelev);
    }

    private void controlGripper()
    {
        if(robot.liftyBoi == null) return;
        boolean toggleGrp = gpad2.just_pressed(ManagedGamepad.Button.X);

        if(toggleGrp) robot.liftyBoi.toggleClampPos();
    }

    private void controlArm()
    {
        robot.liftyBoi.update();
        controlArmElev();
        controlGripper();
    }

    private void controlDrive()
    {
        if (robot.leftMotors.size() == 0 && robot.rightMotors.size() == 0) return;

        raw_lr_x =  gpad1.value(ManagedGamepad.AnalogInput.R_STICK_X);
        raw_fb_y = -gpad1.value(ManagedGamepad.AnalogInput.R_STICK_Y);
        raw_turn =  gpad1.value(ManagedGamepad.AnalogInput.L_STICK_X);

        boolean rgt  = gpad1.pressed(ManagedGamepad.Button.D_RIGHT);
        boolean lft  = gpad1.pressed(ManagedGamepad.Button.D_LEFT);
        boolean fwd  = gpad1.pressed(ManagedGamepad.Button.D_UP);
        boolean bak  = gpad1.pressed(ManagedGamepad.Button.D_DOWN);
        boolean incr = gpad1.just_pressed(ManagedGamepad.Button.R_BUMP);
        boolean decr = gpad1.just_pressed(ManagedGamepad.Button.L_BUMP);
        boolean tglV = gpad1.just_pressed(ManagedGamepad.Button.Y);
        //boolean step_driveType = gpad1.just_pressed(ManagedGamepad.Button.A);

        //if (step_driveType) fieldAlign = !fieldAlign;
        if (tglV) useSetVel = !useSetVel;

        lr_x = ishaper.shape(raw_lr_x, 0.1);
        fb_y = ishaper.shape(raw_fb_y, 0.1);
        turn = ishaper.shape(raw_turn, 0.1);

        if      (incr) dSpd += dStp;
        else if (decr) dSpd -= dStp;
        dSpd = Range.clip(dSpd, 0.0, 1.0);

        if (lft || rgt || fwd || bak)
        {
            lr_x = lft ? -dSpd : rgt ?  dSpd : 0.0;
            fb_y = bak ? -dSpd : fwd ?  dSpd : 0.0;
            if((lft || rgt)  && (fwd || bak))
            {
                lr_x /= spdScl;
                fb_y /= spdScl;
            }
        }

        //Both trig and non-trig versions add turn to left and subtract from right
        double lf =  turn;
        double rf = -turn;
        double lr =  turn;
        double rr = -turn;
        double speed = 0.0;
        double direction = 0.0;

        if(trig)
        {
            //Start of trig based version - allows fieldAlign
            speed = spdScl * Math.sqrt(lr_x * lr_x + fb_y * fb_y);
            direction = Math.atan2(lr_x, fb_y) + rlrAng +
                    (fieldAlign ? Math.toRadians(90.0 - hdg) : 0.0);

            double spdSin = speed * Math.sin(direction);
            double spdCos = speed * Math.cos(direction);

            lf += spdSin;
            rf += spdCos;
            lr += spdCos;
            rr += spdSin;
            //End of trig version
        }
        else
        {
            //Start of non-trig version (w/o fieldAlign):
            lf += fb_y + lr_x;
            rf += fb_y - lr_x;
            rr += fb_y + lr_x;
            lr += fb_y - lr_x;
            //End of non-trig version
        }
        double max = Math.max(Math.max(Math.abs(lf), Math.abs(rf)),
                Math.max(Math.abs(lr), Math.abs(rr)));

        if (max > 1.0)
        {
            lf /= max;
            rf /= max;
            lr /= max;
            rr /= max;
        }

        if (useSetVel)
        {
            robot.lfMotor.setVelocity(lf * maxDPS, AngleUnit.DEGREES);
            robot.rfMotor.setVelocity(rf * maxDPS, AngleUnit.DEGREES);
            robot.lrMotor.setVelocity(lr * maxDPS, AngleUnit.DEGREES);
            robot.rrMotor.setVelocity(rr * maxDPS, AngleUnit.DEGREES);
        } else
        {
            robot.lfMotor.setPower(lf);
            robot.rfMotor.setPower(rf);
            robot.lrMotor.setPower(lr);
            robot.rrMotor.setPower(rr);
        }

        int l = 6;
        dashboard.displayPrintf(l++, "SPD %4.2f DIR %4.2f DSPD: %3.1f FALGN %s USEVEL %s",
                speed, direction, dSpd, fieldAlign, useSetVel);
        dashboard.displayPrintf(l, "OUT: lf %4.2f rf %4.2f lr %4.2f rr %4.2f",
                lf, rf, lr, rr);
    }

    private void processControllerInputs()
    {
        gpad2.update();
        controlArm();
    }

    private void processDriverInputs()
    {
        gpad1.update();
        controlDrive();
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public void runOpMode() throws InterruptedException
    {
        initCommon(this, false, false, false, false);

        initPreStart();

        dashboard.displayPrintf(0, "%s is ready", robot.getName());

        // Wait for the game to start (driver presses PLAY)
        while(!isStarted() && !isStopRequested())
        {
            update();
            printTelem();
            idle();
        }

        RobotLog.dd(TAG, "Mecanum_Driver starting");

        while (opModeIsActive())
        {
            update();
            processControllerInputs();
            processDriverInputs();

            printTelem();
        }
    }

    double dSpd = 0.0;
    double dStp = 0.1;
    static final double diam   = 4.0;  //Inches
    static final double maxIPS = 30.0;
    static final double maxDPS = 360.0 * maxIPS/(diam*Math.PI);
    static final double rlrAng = Math.PI/4.0;
    static final double spdScl = Math.sqrt(2.0);
    static final boolean trig = true;
    Input_Shaper ishaper = new Input_Shaper();
    @SuppressWarnings("FieldCanBeLocal")
    private boolean fieldAlign = false;
    private boolean useSetVel = true;
    private TilerunnerMecanumBot robot = new TilerunnerMecanumBot();
    private ShelbyBot.OpModeType prevOpModeType = ShelbyBot.OpModeType.UNKNOWN;
    private Drivetrain dtrn = new Drivetrain();

    double raw_lr_x;
    double raw_fb_y;
    double raw_turn;
    double lr_x;
    double fb_y;
    double turn;

    int[] cnts = {0,0,0,0};
    double hdg = 0;

    private static final String TAG = "SJH_MTD";
}