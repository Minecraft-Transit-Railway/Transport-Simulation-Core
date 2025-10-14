import {Injectable} from "@angular/core";

@Injectable({providedIn: "root"})
export class DimensionService {
	private dimensions: string[] = [];
	private dimensionIndex = 0;

	public setDimensions(dimensions: string[]) {
		this.dimensions = dimensions;
		this.clampDimensionIndex();
	}

	public setDimension(dimension: string) {
		this.dimensionIndex = this.dimensions.indexOf(dimension);
		this.clampDimensionIndex();
	}

	public getDimensions() {
		return this.dimensions;
	}

	public getDimensionIndex() {
		return this.dimensionIndex;
	}

	private clampDimensionIndex() {
		this.dimensionIndex = Math.max(0, Math.min(this.dimensionIndex, this.dimensions.length));
	}
}
