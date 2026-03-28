import {definePreset} from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";

export const myPreset = definePreset(Aura, {
	semantic: {
		primary: {
			50: "{neutral.50}",
			100: "{neutral.100}",
			200: "{neutral.200}",
			300: "{neutral.300}",
			400: "{neutral.400}",
			500: "{neutral.500}",
			600: "{neutral.600}",
			700: "{neutral.700}",
			800: "{neutral.800}",
			900: "{neutral.900}",
			950: "{neutral.950}",
		},
	},
});
