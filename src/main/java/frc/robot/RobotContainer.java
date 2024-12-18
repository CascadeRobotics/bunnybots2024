 // Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.*;

import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.hardware.ParentDevice;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.utility.PhoenixPIDController;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.pathplanner.lib.commands.FollowPathCommand;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Direction;
import frc.robot.auto.AutoOptions;
import frc.robot.subsystems.Superstructure;
import frc.robot.subsystems.arm.Arm;
import frc.robot.subsystems.arm.ArmConstants;
import frc.robot.subsystems.drivetrain.CommandSwerveDrivetrain;
import frc.robot.subsystems.drivetrain.Telemetry;
import frc.robot.subsystems.drivetrain.TunerConstants;
import frc.robot.subsystems.intake.Intake;
import frc.robot.util.OCXboxController;

public class RobotContainer {
    private double MaxSpeed = TunerConstants.kSpeedAt12Volts.in(MetersPerSecond); // kSpeedAt12Volts desired top speed
    private double MaxAngularRate = RotationsPerSecond.of(1.5).in(RadiansPerSecond); // 1.5 rotations per second max angular velocity

    /* Setting up bindings for necessary control of the swerve drive platform */
    private final SwerveRequest.FieldCentric drive = new SwerveRequest.FieldCentric()
            .withDeadband(MaxSpeed * 0.05).withRotationalDeadband(MaxAngularRate * 0.05) // Add a 10% deadband
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // Use open-loop control for drive motors
    private final SwerveRequest.FieldCentricFacingAngle orient = new SwerveRequest.FieldCentricFacingAngle()
            .withDeadband(MaxSpeed * 0.05).withRotationalDeadband(MaxAngularRate * 0.01) // Add a 10% deadband
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // Use open-loop control for drive motors
    private final SwerveRequest.SwerveDriveBrake brake = new SwerveRequest.SwerveDriveBrake();
    private final SwerveRequest.PointWheelsAt point = new SwerveRequest.PointWheelsAt();

    private final Telemetry logger = new Telemetry(MaxSpeed);

    private final OCXboxController driver = new OCXboxController(0);

    public final CommandSwerveDrivetrain drivetrain = TunerConstants.createDrivetrain();
    public final Intake intake = new Intake();
    public final Arm arm = new Arm();
    public final Superstructure superstructure = new Superstructure(drivetrain, intake, arm);
    public AutoOptions autos  = new AutoOptions(drivetrain, intake, arm, superstructure);


    public RobotContainer() {
        configureBindings();
        setSwerveUpdateFrequency(drivetrain.getModule(0).getDriveMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(0).getSteerMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(1).getDriveMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(1).getSteerMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(2).getDriveMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(2).getSteerMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(3).getDriveMotor());
        setSwerveUpdateFrequency(drivetrain.getModule(3).getSteerMotor());
        ParentDevice.optimizeBusUtilizationForAll(drivetrain.getModule(0).getDriveMotor(), drivetrain.getModule(0).getSteerMotor(), drivetrain.getModule(1).getDriveMotor(), drivetrain.getModule(1).getSteerMotor(), drivetrain.getModule(2).getDriveMotor(), drivetrain.getModule(2).getSteerMotor(), drivetrain.getModule(3).getDriveMotor(), drivetrain.getModule(3).getSteerMotor());
        
        orient.HeadingController = new PhoenixPIDController(7, 0, 0.1);
    }
    
    public void setSwerveUpdateFrequency(TalonFX motor) {
        motor.getDutyCycle().setUpdateFrequency(100);
        motor.getMotorVoltage().setUpdateFrequency(100);
        motor.getPosition().setUpdateFrequency(100);
        motor.getVelocity().setUpdateFrequency(50);
        motor.getStatorCurrent().setUpdateFrequency(50);
    }

    private void configureBindings() {
        //DRIVE COMMAND
        drivetrain.setDefaultCommand(
            drivetrain.applyRequest(() -> {
                var speeds = driver.getSpeeds(MaxSpeed, MaxAngularRate);
                return drive.withVelocityX(speeds.vxMetersPerSecond)
                    .withVelocityY(speeds.vyMetersPerSecond)
                    .withRotationalRate(speeds.omegaRadiansPerSecond);
            })
        );

        driver.a().whileTrue(drivetrain.applyRequest(() -> brake));
        driver.b().whileTrue(drivetrain.applyRequest(() ->
            point.withModuleDirection(new Rotation2d(-driver.getLeftY(), -driver.getLeftX()))
        ));

        // driver.rightTrigger().whileTrue(arm.setVoltageC(-1).finallyDo(arm::stop));

        //INTAKE COMMANDS
        intake.setDefaultCommand(intake.setVoltageC(0.65));
        driver.rightTrigger().whileTrue(superstructure.intake());
        driver.rightBumper().whileTrue(superstructure.outtakeTote());
        driver.back().whileTrue(intake.setVoltageOutC());

        // Decrease arm angle (relatively) slowly while intaking
        driver.leftTrigger()
            .whileTrue(superstructure.decreaseAngle())
            .onFalse(
                arm.setRotationC(ArmConstants.kIntakeAngle)
                    .withTimeout(.2)
                    .onlyIf(()->arm.getTargetRotations() <= ArmConstants.kIntakeAngle.getRotations())
            );

        //Bring the arm to the home position
        driver.povDown().onTrue(arm.setRotationC(ArmConstants.kHomeAngle));

        // reset the robot heading to forward
        driver.start().onTrue(drivetrain.runOnce(() -> drivetrain.seedFieldCentric()));

        //TODO: ADD GYRO BUTTONS FOR AUTO ALIGN
        driver.povUp().whileTrue(drivetrain.applyRequest(()->orient.withTargetDirection(Rotation2d.kZero)));

        // Run SysId routines when holding back/start and X/Y.
        // Note that each routine should be run exactly once in a single log.
        // driver.back().and(driver.y()).whileTrue(drivetrain.sysIdDynamic(Direction.kForward));
        // driver.back().and(driver.x()).whileTrue(drivetrain.sysIdDynamic(Direction.kReverse));
        // driver.start().and(driver.y()).whileTrue(drivetrain.sysIdQuasistatic(Direction.kForward));
        // driver.start().and(driver.x()).whileTrue(drivetrain.sysIdQuasistatic(Direction.kReverse));


        drivetrain.registerTelemetry(logger::telemeterize);
    }

    public void periodic(){
        autos.periodic();
    }

    public void autonomousInit() {
        autos.getAuto().schedule();
    }
    public void robotInit() {
        autos.robotInit();
    }

    public void simulationPeriodic(){
        arm.simulationPeriodic();
    }
}
