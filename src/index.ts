import "dotenv/config";
import readline from "readline";
import { LeadAgent } from "./agents/lead";
import { createPlannerAgent } from "./agents/planner";
import { createUiuxAgent } from "./agents/uiux";
import { createDeveloperAgent } from "./agents/developer";
import { createReviewerAgent } from "./agents/reviewer";

function createTeam(): LeadAgent {
  return new LeadAgent({
    planner: createPlannerAgent(),
    uiux: createUiuxAgent(),
    developer: createDeveloperAgent(),
    reviewer: createReviewerAgent(),
  });
}

async function main(): Promise<void> {
  console.log("\n" + "═".repeat(60));
  console.log("  ✈️   FLIGHT LOG APP  —  AI AGENT TEAM");
  console.log("═".repeat(60));
  console.log("\n👥  Team:");
  console.log("   🎯  Lead Agent       →  claude-sonnet-4-6");
  console.log("   📋  Planner          →  claude-sonnet-4-6");
  console.log("   🎨  UI/UX Designer   →  claude-opus-4-6");
  console.log("   💻  Developer        →  claude-opus-4-6");
  console.log("   🔍  Code Reviewer    →  claude-opus-4-6");
  console.log("\nWorkflow: Lead → Planner → UI/UX → Developer → Reviewer → Lead");
  console.log("\nExample missions:");
  console.log('  "Build the flight log entry screen"');
  console.log('  "Implement the flight history list with search"');
  console.log('  "Create the flight statistics dashboard"');

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  const lead = createTeam();

  const prompt = (): void => {
    rl.question('\n📨  Enter mission (or "exit" to quit):\n> ', async (input) => {
      const mission = input.trim();

      if (mission.toLowerCase() === "exit") {
        console.log("\n👋  Team standing down. Goodbye!\n");
        rl.close();
        return;
      }

      if (!mission) {
        prompt();
        return;
      }

      try {
        await lead.dispatch(mission);
        console.log("═".repeat(60));
        console.log("✅  MISSION COMPLETE");
        console.log("═".repeat(60));
      } catch (err) {
        console.error(
          "\n❌  Error:",
          err instanceof Error ? err.message : String(err)
        );
      }

      prompt();
    });
  };

  prompt();
}

main();
