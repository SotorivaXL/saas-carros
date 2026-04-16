import { redirect } from "next/navigation";

export default function SupervisorDetalhePage() {
    redirect("/protected/dashboard");
}
