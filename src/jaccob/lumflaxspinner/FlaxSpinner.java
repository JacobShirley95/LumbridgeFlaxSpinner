package jaccob.lumflaxspinner;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.concurrent.Callable;

import org.powerbot.script.Area;
import org.powerbot.script.Condition;
import org.powerbot.script.PaintListener;
import org.powerbot.script.PollingScript;
import org.powerbot.script.Script;
import org.powerbot.script.Tile;
import org.powerbot.script.rt4.Bank.Amount;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Component;
import org.powerbot.script.rt4.Constants;
import org.powerbot.script.rt4.Game.Crosshair;
import org.powerbot.script.rt4.Game.Tab;
import org.powerbot.script.rt4.GameObject;
import org.powerbot.script.rt4.Widget;

@Script.Manifest(name = "FlaxSpinner", description = "Spins flax in Lumbridge", properties = "client=4; topic=0;")
public class FlaxSpinner extends PollingScript<ClientContext> implements PaintListener {
	
	enum State {
		WALKING_TO_WHEEL, FINDING_SPINNING_WHEEL, SPINNING, WALKING_TO_BANK, BANKING
	}

	private static final Area BANKING_AREA = new Area(new Tile(3209, 3217), new Tile(3210, 3220));
	private static final Area STAIRCASE_AREA = new Area(new Tile(3203, 3206), new Tile(3207, 3210));
	private static final Area SPINNING_AREA = new Area(new Tile(3208, 3213), new Tile(3211, 3214));
	private static final Tile STAIRCASE_TILE = new Tile(3205, 3208, 2);
	
	private static final int[] STAIRCASE_BOUNDS = {-56, 32, -4, 0, 0, 72};
	private static final int[] STAIRCASE_MIDDLE_BOUNDS = {-64, 20, -104, 0, 8, 108};
	
	private static final int[] SPINNER_BOUNDS = {-16, 12, -144, -68, -60, 8};
	
	private static final int SPINNING_WHEEL_ID = 14889;
	private static final int BOW_STRING_ID = 1777;
	private static final int FLAX_ID = 1779;
	
	private static final int CRAFT_WIDGET_ID = 459;
	private static final int BOW_STRING_COMP_ID = 91;
	
	private static final int ENTER_AMOUNT_WIDGET_ID = 162;
	private static final int ENTER_AMOUNT_COMP_ID = 32;
	
	private static final Tile DOOR_TILE = new Tile(3207, 3214, 1);
	private static final int[] DOOR_BOUNDS = {116, 140, -228, 0, -4, 120};
	
	private State state = null;
	private long spinningTimer = 0;
	private int lastStringCount = 0;
	private int bowStringsMade = 0;
	
	@Override
	public void start() {
		super.start();
		
		if (ctx.game.floor() == 2)
			state = State.BANKING;
		else if (ctx.game.floor() == 1)
			state = State.WALKING_TO_BANK;
		
		//walkToBank();
	}
	
	private boolean handleDoor() {
		GameObject doorObject = ctx.objects.select().at(DOOR_TILE).id(1543).peek();
		if (doorObject != null && doorObject.valid()) {
			doorObject.bounds(DOOR_BOUNDS);
			
			Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					return ctx.movement.distance(ctx.movement.destination()) < 2 || doorObject.inViewport();
				}
			});
			
			if (!doorObject.inViewport()) {
				ctx.camera.turnTo(doorObject);
				ctx.camera.pitch(randomRange(30, 60));
			}
				
			for (int tries = 0; tries < 3; tries++) {
				if (doorObject.valid()) {
					doorObject.bounds(DOOR_BOUNDS);
					if (doorObject.interact("Open")) {
						Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return !doorObject.valid();
							}
						}, 300, 10);
						
						return !doorObject.valid();
					} else 
						return false;
				} else
					break;
			}
		}
		
		return true;
	}
	
	//door = 1543
	private boolean useSpinningWheel() {
		GameObject spinningWheel = ctx.objects.select().id(SPINNING_WHEEL_ID).peek();
		spinningWheel.bounds(SPINNER_BOUNDS);
		
		if (!spinningWheel.inViewport())
			ctx.movement.step(SPINNING_AREA.getRandomTile());
		
		Condition.wait(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return spinningWheel.inViewport();
			}
		});
		
		if (!spinningWheel.inViewport()) {
			ctx.camera.pitch(randomRange(70, 99));
			ctx.camera.angle(randomRange(170, 190));
		}
		
		Widget widget = ctx.widgets.widget(CRAFT_WIDGET_ID);
		Component comp = widget.component(BOW_STRING_COMP_ID);
		
		Widget chatWidget = ctx.widgets.widget(ENTER_AMOUNT_WIDGET_ID);
		Component enterAmountComp = chatWidget.component(ENTER_AMOUNT_COMP_ID);

		for (int tries = 0; tries < 5; tries++) {
			if (handleDoor() && spinningWheel.interact(false, "Spin")) {
				if (Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						return comp.visible();
					}
				})) {
					for (int tries2 = 0; tries2 < 5; tries2++) {
						if (comp.interact("Make X")) {
							if (Condition.wait(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {
									return enterAmountComp.visible();
								}
							})) {
								ctx.input.sendln("33");
								
								spinningTimer = System.currentTimeMillis();
								lastStringCount = 0;
								
								return true;
							}
						}
					}
					break;
				}
			}
		}
		
		return false;
	}
	
	private boolean timeToBank() {
		if (ctx.inventory.select().id(FLAX_ID).isEmpty())
			return ctx.game.tab(Tab.INVENTORY);
		
		return false;
	}
	
	private int randomRange(int min, int max) {
		return min + ((int)Math.random() * (max - min));
	}
	
	private void walkToBank() {
		GameObject middleStaircase = getStaircaseMiddle();
		
		for (int tries = 0; tries < 5; tries++) {
			if (handleDoor()) {
				if (!middleStaircase.inViewport()) {
					ctx.camera.pitch(randomRange(30, 50));
					ctx.camera.turnTo(middleStaircase, randomRange(-50, 50));
				} else {
					if (middleStaircase.interact("Climb-up")) {
						Tile bankTile = BANKING_AREA.getRandomTile();
						ctx.input.move(bankTile.matrix(ctx).mapPoint());
						ctx.camera.pitch(randomRange(75, 99));
						if (Condition.wait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								return ctx.game.floor() == 2;
							}
						}, 300, 15)) {
							System.out.println("Climbed up!");
							
	
							break;
						}
					}
				}
			}
		}
		
		Tile bankTile = BANKING_AREA.getRandomTile();
		if (ctx.movement.step(bankTile)) {
			Tile actualBankTile = ctx.bank.nearest().tile();
			ctx.camera.turnTo(actualBankTile, 130 + randomRange(-20, 20));
			
			if (Condition.wait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					
					return ctx.bank.inViewport();
				}
			}));
		}
	}
	
	public boolean walkToWheel() {
		GameObject staircase = getStaircase();
		
		for (int tries = 0; tries < 10; tries++) {
			Tile ladderTile = STAIRCASE_AREA.getRandomTile();
			if (ctx.movement.step(ladderTile)) {
				ctx.camera.turnTo(staircase, randomRange(-20, 20));
				ctx.camera.pitch(randomRange(80, 99));

				if (Condition.wait(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						
						return staircase.inViewport();
					}
				}, 500, 6)) {
					Tile spinningAreaTile = SPINNING_AREA.getRandomTile();
					
					for (int tries2 = 0; tries2 < 7; tries2++) {
						if (staircase.interact("Climb-down")) {
							
							ctx.input.move(spinningAreaTile.matrix(ctx).mapPoint());
							if (Condition.wait(new Callable<Boolean>() {
								@Override
								public Boolean call() throws Exception {
									return ctx.game.floor() == 1;
								}
							}, 300, 15)) {
								Condition.sleep(100);
								
								return true;
							}
						}
					}
				}
			}
		}
		
		return false;
	}
	
	private int countFlax() {
		return ctx.inventory.select().id(FLAX_ID).count();
	}
	
	public void antiban() {
		if (countFlax() > 5) {
			ctx.game.tab(Tab.INVENTORY);
			if (Math.random() > 0.8 && !ctx.menu.items()[0].contains("Climb")) {
				GameObject scm = getStaircaseMiddle();
				scm.hover();
			}
		} else {
			if (Math.random() > 0.95) {
				ctx.camera.angle(180 + randomRange(-20, 20));
			} else if (Math.random() > 0.95) {
				ctx.camera.pitch(randomRange(20, 99));
			} else if (Math.random() > 0.98) {
				hoverSkill(13);
				ctx.game.tab(Tab.INVENTORY);
			}
		}
	}
	
	private void hoverSkill(int id) {
		ctx.game.tab(Tab.STATS);
		ctx.widgets.widget(320).component(id).hover();
		Condition.sleep((int)(1500 + (Math.random() * 2000)));
	}
	
	private boolean nearlyBankTime() {
		return ctx.inventory.select().id(FLAX_ID).count() <= randomRange(5, 1);
	}
	
	private boolean interactSpecial(GameObject obj, String action) {
		return Math.random() > 0.9 ? obj.interact(action) : obj.interact(false, action);
	}
	
	private boolean amISpinning() {
		long time = System.currentTimeMillis();
		if (time - spinningTimer > 5000) {
			spinningTimer = time;
			
			int c = ctx.inventory.select().id(BOW_STRING_ID).count();
			if (c == lastStringCount) {
				return false;
			}
			
			lastStringCount = c;
		}
		return true;
	}
	
	private void run() {
		if (ctx.movement.energyLevel() >= 25) 
			ctx.movement.running(true);
	}
	
	@Override
	public void poll() {
		switch (state) {
		case BANKING:
			if (bank()) 
				state = State.WALKING_TO_WHEEL;
			break;
		case WALKING_TO_WHEEL:
			run();
			walkToWheel();
			state = State.FINDING_SPINNING_WHEEL;
			break;
		case FINDING_SPINNING_WHEEL:
			useSpinningWheel();
			state = State.SPINNING;
			break;
		case SPINNING: 
			if (timeToBank()) {
				bowStringsMade += 28;
				ctx.game.tab(Tab.INVENTORY);
				state = State.WALKING_TO_BANK;
			} else if (ctx.chat.canContinue()) {
				Condition.sleep(randomRange(1000, 3000));
				state = state.FINDING_SPINNING_WHEEL;
			} else if (!amISpinning()) {
				state = state.FINDING_SPINNING_WHEEL;
			} else {
				antiban();
			}
				
			break;
		case WALKING_TO_BANK:
			run();
			walkToBank();
			state = State.BANKING;
			break;
		}
	}
	
	private GameObject getStaircaseMiddle() {
		GameObject staircase = ctx.objects.select().id(16672).nearest().peek();
		staircase.bounds(STAIRCASE_MIDDLE_BOUNDS);
		
		return staircase;
	}
	
	private GameObject getStaircase() {
		GameObject staircase = ctx.objects.select().at(STAIRCASE_TILE).id(16673).peek();
		staircase.bounds(STAIRCASE_BOUNDS);
		
		return staircase;
	}
	
	public boolean bank() {
		ctx.bank.open();
		ctx.bank.depositInventory();
		ctx.bank.withdraw(FLAX_ID, 28);
		
		return true;
	}

	@Override
	public void repaint(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;
		g2.setColor(Color.GREEN);
		g2.drawString("Bow strings made: " + bowStringsMade, 10, 50);
	}
	
}
