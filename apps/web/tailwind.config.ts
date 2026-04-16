import type { Config } from "tailwindcss";

const config: Config = {
    content: [
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                io: {
                    purple: "#6d5efc",
                    purple2: "#9f8cff",
                    dark: "#121212",
                    white: "#ffffff",
                    light: "#f3f3f3",
                    slate: "#7a7a7a",
                    panel: "#ebebeb",
                },
            },
            boxShadow: {
                soft: "0 10px 30px rgba(0,0,0,0.12)",
            },
        },
    },
    plugins: [],
};

export default config;
