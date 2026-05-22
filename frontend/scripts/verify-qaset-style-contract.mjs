import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = "/Users/wyw/Desktop/Project/QA_Agent/frontend";
const repositoryCss = readFileSync(join(root, "src/styles/pages/repository.css"), "utf8");
const questionCss = readFileSync(join(root, "src/styles/pages/question.css"), "utf8");

const requiredSelectors = [
    ".tree-qaSetEntry",
    ".tree-qaSetEntry--entry",
    ".tree-qaSetEntry--active",
    ".repository-qaSetEntry-list",
    ".repository-qaSetEntry-card",
    ".repository-qaSetEntry-card__meta",
    ".tag-dialog__selected-qaSetEntry",
    ".tag-dialog__pool-qaSetEntry",
];

const styleSource = `${repositoryCss}\n${questionCss}`;
const missingSelectors = requiredSelectors.filter((selector) => !styleSource.includes(selector));

if (missingSelectors.length > 0) {
    console.error("Missing CSS selectors:");
    for (const selector of missingSelectors) {
        console.error(`- ${selector}`);
    }
    process.exit(1);
}

console.log("QASet style contract verified.");
