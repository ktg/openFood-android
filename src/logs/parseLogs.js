const fs = require('fs');
const accelDelta = 0.1;
const gyroDelta = 50;
const activeCount = 10;
const inactiveCount = 50;

function readFile(file) {
	if (fs.lstatSync(file).isDirectory()) {
		const children = fs.readdirSync(file);
		for (const child of children) {
			const path = file + '/' + child;
			readFile(path);
		}
	}
	else if (file.endsWith('.csv')) {
		const sensors = {};
		const lines = fs.readFileSync(file, 'utf-8')
			.split('\n');
		for (const line of lines) {
			const split = line.split(',');
			const time = Date.parse(split[0]);
			const id = split[1]; // + '/' + split[2];
			if (id) {
				const parts = id.split(':');
				if (parts.length === 6) {
					let sensor = sensors[id];
					if (sensor) {
						const delay = time - sensor.end;
						sensor.end = time;
						sensor.maxDelay = Math.max(sensor.maxDelay, delay);
						sensor.count += 1;
					} else {
						sensor = {
							id: id,
							start: time,
							end: time,
							maxDelay: 0,
							count: 1,
							activity: [],
							active: false,
							activeCount: 0,
							av: 0
						};
						sensors[id] = sensor;
					}

					if (split[2] === 'accel') {
						const ax = parseFloat(split[3]);
						const ay = parseFloat(split[4]);
						const az = parseFloat(split[5]);

						if ('ax' in sensor) {
							if(changed(ax + ay + az, sensor.av, accelDelta)) {
//							if (changed(ax, sensor.ax, accelDelta) || changed(ay, sensor.ay, accelDelta) || changed(az, sensor.az, accelDelta)) {
								if(!sensor.active) {
									if(!'activeCount' in sensor || sensor.activeCount === 0) {
										sensor.activeCount = 1;
										sensor.activeStart = time;
									} else {
										sensor.activeCount += 1;
										if(sensor.activeCount >= activeCount) {
											sensor.active = true;
											sensor.activeCount = 0;
										}
									}
								} else {
									sensor.inactiveCount = 0;
								}
							} else {
								if(sensor.active) {
									if(!'inactiveCount' in sensor || sensor.inactiveCount === 0) {
										sensor.inactiveCount = 1;
										sensor.activeEnd = time;
									} else {
										sensor.inactiveCount += 1;
										if(sensor.inactiveCount >= inactiveCount) {
											sensor.inactiveCount = 0;
											sensor.active = false;
											sensor.activity.push({
												start: sensor.activeStart,
												end: sensor.activeEnd
											});
										}
									}
								} else {
									sensor.activeCount = 0;
								}
							}
						}
						else {
							sensor.av = ax + ay + az;
						}

						sensor.ax = ax;
						sensor.ay = ay;
						sensor.az = az;
						sensor.av = (ax + ay + az + sensor.av) / 2.0;
						//console.log(sensor.av);
					} else {
						// const gx = split[3];
						// const gy = split[4];
						// const gz = split[5];
						//
						// if ('gx' in sensor) {
						// 	if (changed(gx, sensor.gx, gyroDelta) || changed(gy, sensor.gy, gyroDelta) || changed(gz, sensor.gz, gyroDelta)) {
						// 		if(!sensor.active) {
						// 			sensor.active = true;
						// 			sensor.activeStart = time;
						// 		}
						// 	} else {
						// 		sensor.activeCount = 0;
						// 		if(sensor.active) {
						// 			sensor.active = false;
						// 			sensor.activity.push({
						// 				start: sensor.activeStart,
						// 				end: time
						// 			})
						// 		}
						// 	}
						// }
						//
						// sensor.gx = gx;
						// sensor.gy = gy;
						// sensor.gz = gz;
					}

				}
			}
		}
		let header = false;
		for (const id in sensors) {
			const sensor = sensors[id];
			const length = sensor.end - sensor.start;
			if (length > 180000) {
				if (!header) {
					header = true;
					console.log(file);
				}
				console.log(sensor.id + ": start = " + new Date(sensor.start).toUTCString() + "\t length = " + Math.round(length / 60000) + "mins, avg delay = " + Math.round(length / sensor.count) + "ms, max delay = " + Math.round(sensor.maxDelay / 1000) + "secs");
				for(const active of sensor.activity) {
					const length = active.end - active.start;
					console.log("\t Active for " + Math.round(length / 1000) + "s @ " + new Date(active.start).toUTCString());
				}
			}
		}
	}
}

function changed(a, b, delta) {
	return (b - a) > delta || (b - a) < -delta;
}

readFile(__dirname);