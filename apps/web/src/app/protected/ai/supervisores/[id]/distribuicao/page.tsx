import { redirect } from "next/navigation";

export default function SupervisorDistribuicaoPage() {
    redirect("/protected/dashboard");
}
