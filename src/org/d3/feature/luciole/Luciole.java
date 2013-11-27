package org.d3.feature.luciole;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Luciole implements Runnable {
	public static final long DELAY_INIT_MIN = 500;
	public static final long DELAY_INIT_AVG = 250;
	public static final long DELTA = 250;
	public static final long RANDOM = 50;
	public static final long MIN_DELAY = 250;

	String id;
	Foret foret;
	boolean alive;
	long delay, max;
	ReentrantLock lock = new ReentrantLock();
	HashMap<String, Long> messages = new HashMap<String, Long>();
	Condition cond = lock.newCondition();
	int neigh = 0;
	long lastFlashAt;

	public Luciole(Foret foret, String id) {
		this.foret = foret;
		this.id = id;
		delay = DELAY_INIT_MIN + (long) (Math.random() * DELAY_INIT_AVG);
		max = delay;
		alive = true;
		lastFlashAt = System.currentTimeMillis();
		
		Thread t = new Thread(this, id);
		t.setDaemon(true);
		t.start();
	}

	public void run() {
		try {
			foret.lock.lock();
			foret.starter.await();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} finally {
			foret.lock.unlock();
		}

		while (alive) {
			addRandomToDelay();
			lastFlashAt = System.currentTimeMillis();
			foret.flash(id, max);

			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {

			}

			max = waitForFriends();
		}
	}

	void addRandomToDelay() {
		delay += (long) ((Math.random() * 2 - 1) * RANDOM);
		delay = Math.max(MIN_DELAY, delay);
	}

	long waitForFriends() {
		long date = System.currentTimeMillis() + (max - delay) + DELTA;
		long m = delay;
		while (!allMessagesReceived() && date > System.currentTimeMillis()) {
			try {
				long d = Math.max(0, date - System.currentTimeMillis());
				lock.lock();
				cond.await(d, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
			} finally {
				lock.unlock();
			}
		}

		try {
			lock.lock();

			for (Long d : messages.values())
				m = Math.max(m, d);

			messages.clear();
		} finally {
			lock.unlock();
		}

		return m;
	}

	boolean allMessagesReceived() {
		boolean r = false;

		try {
			lock.lock();
			r = messages.size() >= neigh;
		} finally {
			lock.unlock();
		}

		return r;
	}

	public void signal(String id, long d) {
		try {
			lock.lock();
			messages.put(id, d);
			cond.signal();
		} finally {
			lock.unlock();
		}
	}

	public void swat() {
		alive = false;
	}
}
